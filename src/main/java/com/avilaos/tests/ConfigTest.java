package com.avilaos.tests;

import com.avilaos.config.BufferConfig;
import com.avilaos.config.ClusterFactory;
import com.avilaos.config.ComputerConfig;
import com.avilaos.config.ConfigLoader;
import com.avilaos.config.ConfigValidator;
import com.avilaos.config.ProcessConfig;
import com.avilaos.config.SimulationConfig;
import com.avilaos.enums.SchedulingPolicy;
import com.avilaos.kernel.EventLogger;
import com.avilaos.kernel.Kernel;
import com.avilaos.system.Cluster;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

// Prueba de la configuración JSON. Se corre con Shift+F6 sobre este archivo.
// Necesita que exista config/default.json en la carpeta del proyecto.
public class ConfigTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws IOException {
        testRoundTrip();
        testDefaultJson();
        testRejects();
        testBadFiles();
        System.out.println("--------------------------------");
        System.out.println("Pruebas OK: " + passed + " | Fallidas: " + failed);
    }

    // Guardar y volver a cargar debe dar los mismos valores
    private static void testRoundTrip() throws IOException {
        SimulationConfig original = sample();
        File file = File.createTempFile("avilaos-config", ".json");
        file.deleteOnExit();
        ConfigLoader.save(original, file);
        SimulationConfig loaded = ConfigLoader.load(file);
        check("round-trip: ciclo y latencia iguales",
                loaded.cycleDurationMs == original.cycleDurationMs
                && loaded.networkLatency == original.networkLatency);
        check("round-trip: computadores iguales", sameComputers(original, loaded));
        check("round-trip: buffers iguales", sameBuffers(original, loaded));
        check("round-trip: procesos iguales", sameProcesses(original, loaded));
        check("lo cargado es válido", !rejects(loaded));
    }

    // El archivo de ejemplo debe construir el clúster completo
    private static void testDefaultJson() {
        File file = new File("config/default.json");
        check("existe config/default.json", file.exists());
        if (!file.exists()) {
            return;
        }
        SimulationConfig cfg = ConfigLoader.load(file);
        Cluster cluster = ClusterFactory.build(cfg, EventLogger.SILENT);
        check("default.json: 2 computadores", cluster.getComputerCount() == 2);
        check("default.json: 1 buffer", cluster.getBuffersSnapshot().length == 1);
        check("default.json: 4 procesos", countProcesses(cluster) == 4);
        check("default.json: latencia de red 2", cluster.getNetworkLatency() == 2);
        check("default.json: PC0 en ROUND_ROBIN",
                cluster.getComputer(0).getKernel().getSchedulingPolicy() == SchedulingPolicy.ROUND_ROBIN);
        check("default.json: PC1 en EDF",
                cluster.getComputer(1).getKernel().getSchedulingPolicy() == SchedulingPolicy.EDF);
        check("default.json: quantum del PC0 es 3", cluster.getComputer(0).getKernel().getQuantum() == 3);
    }

    // Cada configuración mala debe ser rechazada
    private static void testRejects() {
        check("la configuración de ejemplo es válida", !rejects(sample()));

        SimulationConfig c = sample();
        c.computers = new ComputerConfig[]{c.computers[0]};
        check("rechaza 1 solo computador", rejects(c));

        c = sample();
        c.computers[0].ramCapacity = 0;
        check("rechaza RAM 0", rejects(c));

        c = sample();
        c.computers[0].quantum = 0;
        check("rechaza quantum 0", rejects(c));

        c = sample();
        c.networkLatency = -1;
        check("rechaza latencia negativa", rejects(c));

        c = sample();
        c.cycleDurationMs = 10001;
        check("rechaza ciclo mayor a 10000 ms", rejects(c));

        c = sample();
        c.computers[1].policy = "XYZ";
        check("rechaza política inválida", rejects(c));

        c = sample();
        c.processes[0].type = "XYZ";
        check("rechaza tipo 'XYZ'", rejects(c));

        c = sample();
        c.processes[2].bufferId = "NO_EXISTE";
        check("rechaza buffer inexistente", rejects(c));

        c = sample();
        c.buffers = new BufferConfig[]{new BufferConfig("B1", 5, 0), new BufferConfig("B1", 3, 1)};
        check("rechaza id de buffer repetido", rejects(c));

        c = sample();
        c.processes[0].memory = 9999;
        check("rechaza memoria mayor que la RAM", rejects(c));

        c = sample();
        c.processes[0].name = "   ";
        check("rechaza nombre vacío", rejects(c));

        c = sample();
        c.processes[1].ioEvery = 99;
        check("rechaza E/S cada N mayor que las instrucciones", rejects(c));

        c = sample();
        c.processes[0].computerId = 5;
        check("rechaza computador inexistente", rejects(c));
    }

    private static void testBadFiles() throws IOException {
        File bad = File.createTempFile("avilaos-bad", ".json");
        bad.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(bad)) {
            out.write("{ esto no es json".getBytes("UTF-8"));
        }
        check("rechaza JSON mal formado", loadFails(bad));
        check("rechaza archivo que no existe", loadFails(new File("no-existe-12345.json")));
    }

    // ---------------------------------------------------------------- auxiliares

    // Misma configuración que config/default.json
    private static SimulationConfig sample() {
        SimulationConfig c = new SimulationConfig();
        c.cycleDurationMs = 500;
        c.networkLatency = 2;
        c.computers = new ComputerConfig[]{
            new ComputerConfig(256, "ROUND_ROBIN", 3),
            new ComputerConfig(256, "EDF", 3)};
        c.buffers = new BufferConfig[]{new BufferConfig("B1", 5, 0)};
        c.processes = new ProcessConfig[]{
            process("Editor", "CPU_BOUND", 0, 2, 32, 200, 20),
            process("Descarga", "IO_BOUND", -1, 1, 16, 300, 15),
            process("Sensor", "PRODUCER", 0, 1, 16, 400, 0),
            process("Monitor", "CONSUMER", 1, 1, 16, 400, 0)};
        c.processes[1].ioEvery = 3;
        c.processes[1].ioDuration = 4;
        c.processes[2].bufferId = "B1";
        c.processes[2].items = 5;
        c.processes[2].workCycles = 2;
        c.processes[3].bufferId = "B1";
        c.processes[3].items = 5;
        c.processes[3].workCycles = 1;
        return c;
    }

    private static ProcessConfig process(String name, String type, int computer, int priority,
            int memory, int deadline, int instructions) {
        ProcessConfig p = new ProcessConfig();
        p.name = name;
        p.type = type;
        p.computerId = computer;
        p.priority = priority;
        p.memory = memory;
        p.deadline = deadline;
        p.instructions = instructions;
        return p;
    }

    private static boolean rejects(SimulationConfig c) {
        try {
            ConfigValidator.validate(c);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean loadFails(File f) {
        try {
            ConfigLoader.load(f);
            return false;
        } catch (IllegalArgumentException e) {
            return e.getMessage().startsWith("Archivo inválido");
        }
    }

    // Suma las 4 colas de cada computador y el proceso en CPU
    private static int countProcesses(Cluster cluster) {
        int total = 0;
        for (int i = 0; i < cluster.getComputerCount(); i++) {
            Kernel k = cluster.getComputer(i).getKernel();
            total += k.getNewSnapshot().length + k.getReadySnapshot().length
                    + k.getBlockedSnapshot().length + k.getTerminatedSnapshot().length
                    + (k.getCpu().getRunning() != null ? 1 : 0);
        }
        return total;
    }

    private static boolean sameComputers(SimulationConfig a, SimulationConfig b) {
        if (a.computers.length != b.computers.length) {
            return false;
        }
        for (int i = 0; i < a.computers.length; i++) {
            if (a.computers[i].ramCapacity != b.computers[i].ramCapacity
                    || a.computers[i].quantum != b.computers[i].quantum
                    || !a.computers[i].policy.equals(b.computers[i].policy)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameBuffers(SimulationConfig a, SimulationConfig b) {
        if (a.buffers.length != b.buffers.length) {
            return false;
        }
        for (int i = 0; i < a.buffers.length; i++) {
            if (!a.buffers[i].id.equals(b.buffers[i].id)
                    || a.buffers[i].capacity != b.buffers[i].capacity
                    || a.buffers[i].hostComputerId != b.buffers[i].hostComputerId) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameProcesses(SimulationConfig a, SimulationConfig b) {
        if (a.processes.length != b.processes.length) {
            return false;
        }
        for (int i = 0; i < a.processes.length; i++) {
            ProcessConfig x = a.processes[i];
            ProcessConfig y = b.processes[i];
            boolean same = x.name.equals(y.name) && x.type.equals(y.type)
                    && x.computerId == y.computerId && x.priority == y.priority
                    && x.memory == y.memory && x.deadline == y.deadline
                    && x.instructions == y.instructions && x.ioEvery == y.ioEvery
                    && x.ioDuration == y.ioDuration && x.items == y.items
                    && x.workCycles == y.workCycles
                    && (x.bufferId == null ? y.bufferId == null : x.bufferId.equals(y.bufferId));
            if (!same) {
                return false;
            }
        }
        return true;
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("[OK]    " + name);
        } else {
            failed++;
            System.out.println("[FALLA] " + name);
        }
    }
}
