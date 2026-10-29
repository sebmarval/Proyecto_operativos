/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.tests;

import com.avilaos.enums.ProcessState;
import com.avilaos.enums.ProcessType;
import com.avilaos.enums.SchedulingPolicy;
import com.avilaos.kernel.EventLogger;
import com.avilaos.metrics.GlobalMetrics;
import com.avilaos.metrics.MetricsCollector;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.sync.BoundedBuffer;
import com.avilaos.sync.BufferStatus;
import com.avilaos.system.Cluster;

/**
 * Experimentos reproducibles para las conclusiones del informe:
 *  1. La MISMA carga de trabajo con cada política de planificación.
 *  2. La MISMA transferencia productor-consumidor en modo local y remoto (varias latencias).
 *  3. Efecto de la capacidad del buffer con varios productores y consumidores.
 *
 * Todo avanza con ticks manuales (sin hilos ni azar), así que cada corrida da
 * exactamente los mismos números: cualquiera puede repetirlos en la defensa.
 * Imprime tablas en formato Markdown listas para copiar al informe.
 *
 * Ejecutar en NetBeans: clic derecho sobre este archivo -> Run File (Shift+F6).
 *
 * @author SebMarval
 */
public class ExperimentsRunner {

    private static final int MAX_CYCLES = 5000;

    /**
     * Carga de trabajo común: {nombre, computador, tipo (0 = CPU, 1 = E/S), prioridad,
     * memoria, deadline, instrucciones, ciclo de llegada, E/S cada N, E/S dura M}.
     * Mezcla procesos largos y cortos, urgentes y holgados, y llegadas escalonadas.
     */
    private static final int[][] WORKLOAD = {
        // PC0
        {0, 0, 0, 3, 32, 140, 24, 0, 0, 0},   // Largo0
        {1, 0, 1, 2, 16, 120, 12, 0, 3, 4},   // ES0
        {2, 0, 0, 4, 16, 60, 10, 2, 0, 0},    // Medio0
        {3, 0, 0, 1, 16, 18, 4, 6, 0, 0},     // Urgente0 (llega tarde, deadline corto)
        {4, 0, 0, 5, 16, 90, 8, 10, 0, 0},    // Relleno0
        {5, 0, 0, 0, 16, 25, 5, 14, 0, 0},    // Critico0
        // PC1
        {6, 1, 1, 1, 16, 110, 10, 0, 2, 5},   // ES1
        {7, 1, 0, 3, 32, 130, 20, 0, 0, 0},   // Largo1
        {8, 1, 0, 2, 16, 45, 9, 3, 0, 0},     // Medio1
        {9, 1, 0, 0, 16, 16, 4, 8, 0, 0},     // Urgente1
        {10, 1, 0, 4, 16, 100, 12, 12, 0, 0}, // Relleno1
    };
    private static final String[] NAMES = {"Largo0", "ES0", "Medio0", "Urgente0", "Relleno0", "Critico0",
        "ES1", "Largo1", "Medio1", "Urgente1", "Relleno1"};

    public static void main(String[] args) {
        experimentPolicies();
        experimentLocalVsRemote();
        experimentBufferCapacity();
    }

    // ===================================================================== 1
    private static void experimentPolicies() {
        System.out.println("## Experimento 1 — Misma carga con cada política (2 computadores, 11 procesos)");
        System.out.println();
        System.out.println("| Política | Ciclos totales | Deadlines cumplidos | Vencidos | Respuesta prom. | Espera en listos prom. | Retorno prom. | Equidad (Jain) | Utilización CPU |");
        System.out.println("|---|---|---|---|---|---|---|---|---|");
        runPolicy("FCFS", SchedulingPolicy.FCFS, 3);
        runPolicy("EDF", SchedulingPolicy.EDF, 3);
        runPolicy("Round Robin q=2", SchedulingPolicy.ROUND_ROBIN, 2);
        runPolicy("Round Robin q=5", SchedulingPolicy.ROUND_ROBIN, 5);
        runPolicy("Prioridades apropiativas", SchedulingPolicy.PREEMPTIVE_PRIORITY, 3);
        System.out.println();
    }

    private static void runPolicy(String label, SchedulingPolicy policy, int quantum) {
        Cluster cl = new Cluster(new int[]{256, 256}, policy, quantum, 0, 0, EventLogger.SILENT);
        ProcessControlBlock[] all = new ProcessControlBlock[WORKLOAD.length];
        int cycle = 0;
        while (cycle < MAX_CYCLES && !(allSubmitted(all) && done(all))) {
            for (int i = 0; i < WORKLOAD.length; i++) {
                if (all[i] == null && WORKLOAD[i][7] == cycle) {
                    all[i] = create(WORKLOAD[i]);
                    cl.submit(all[i]);
                }
            }
            cycle++;
            tick(cl, cycle);
        }
        GlobalMetrics g = new MetricsCollector(cl).computeGlobal();
        String missed = "";
        int waitSum = 0;
        int turnaroundSum = 0;
        for (ProcessControlBlock p : all) {
            waitSum += p.getReadyWaitCycles();
            turnaroundSum += p.getTurnaroundTime();
            if (p.isDeadlineMissed()) {
                missed += (missed.isEmpty() ? "" : ", ") + p.getName();
            }
        }
        System.out.printf("| %s | %d | %.1f %% | %d (%s) | %.2f | %.2f | %.2f | %.3f | %.1f %% |%n",
                label, cycle, g.deadlineCompliance(), g.deadlineMissed(), missed.isEmpty() ? "—" : missed,
                g.avgResponseTime(), (double) waitSum / all.length, (double) turnaroundSum / all.length,
                g.fairness(), g.cpuUtilization());
    }

    private static ProcessControlBlock create(int[] w) {
        ProcessType type = (w[2] == 1) ? ProcessType.IO_BOUND : ProcessType.CPU_BOUND;
        ProcessControlBlock p = new ProcessControlBlock(NAMES[w[0]], w[1], type, w[3], w[4], w[5], w[6]);
        if (type == ProcessType.IO_BOUND) {
            p.configureIO(w[8], w[9]);
        }
        return p;
    }

    // ===================================================================== 2
    /** Productor en PC0 (buffer en PC0); el consumidor en PC0 (local) o en PC1 (remoto). */
    private static void experimentLocalVsRemote() {
        System.out.println("## Experimento 2 — Productor-consumidor local vs remoto (8 elementos, buffer de capacidad 2 en PC0)");
        System.out.println();
        System.out.println("| Consumidor | Latencia | Ciclos totales | Elementos por 100 ciclos | Bloqueado en semáforos (prom.) | Bloqueado por red (consumidor) | Utilización PC0 | Utilización PC1 |");
        System.out.println("|---|---|---|---|---|---|---|---|");
        runTransfer(false, 0);
        int[] latencies = {0, 1, 2, 4, 8};
        for (int latency : latencies) {
            runTransfer(true, latency);
        }
        System.out.println();
    }

    private static void runTransfer(boolean remote, int latency) {
        Cluster cl = new Cluster(new int[]{256, 256}, SchedulingPolicy.FCFS, 3, 0, latency, EventLogger.SILENT);
        BoundedBuffer buf = cl.createBuffer("B", 2, 0);
        ProcessControlBlock prod = ProcessControlBlock.createProducer("Prod", 0, 1, 16, 9000, buf, 8, 2);
        ProcessControlBlock cons = ProcessControlBlock.createConsumer("Cons", remote ? 1 : 0, 1, 16, 9000, buf, 8, 2);
        cl.submit(prod);
        cl.submit(cons);
        int cycle = 0;
        while (cycle < MAX_CYCLES && !done(prod, cons)) {
            cycle++;
            tick(cl, cycle);
        }
        GlobalMetrics g = new MetricsCollector(cl).computeGlobal();
        int networkBlocked = cons.getBlockedCycles() - cons.getSemaphoreBlockedCycles();
        System.out.printf("| %s | %d | %d | %.2f | %.2f | %d | %.1f %% | %.1f %% |%n",
                remote ? "Remoto (PC1)" : "Local (PC0)", latency, cycle, 800.0 / cycle,
                g.avgSemaphoreBlocked(), networkBlocked,
                cl.getComputer(0).getKernel().getCpu().getUtilizationPercent(),
                cl.getComputer(1).getKernel().getCpu().getUtilizationPercent());
    }

    // ===================================================================== 3
    /** 2 productores + 2 consumidores locales (RR q=2) con distintas capacidades de buffer. */
    private static void experimentBufferCapacity() {
        System.out.println("## Experimento 3 — Capacidad del buffer (2 productores + 2 consumidores locales, 12 elementos, RR q=2)");
        System.out.println();
        System.out.println("| Capacidad | Ciclos totales | Bloqueado en semáforos (prom.) | Bloqueos por lleno | Bloqueos por vacío | Bloqueos por mutex |");
        System.out.println("|---|---|---|---|---|---|");
        int[] capacities = {1, 2, 4, 8};
        for (int capacity : capacities) {
            runCapacity(capacity);
        }
        System.out.println();
    }

    private static void runCapacity(int capacity) {
        Cluster cl = new Cluster(new int[]{256, 256}, SchedulingPolicy.ROUND_ROBIN, 2, 0, 0, EventLogger.SILENT);
        BoundedBuffer buf = cl.createBuffer("B", capacity, 0);
        ProcessControlBlock[] ps = {
            ProcessControlBlock.createProducer("P1", 0, 1, 16, 9000, buf, 6, 1),
            ProcessControlBlock.createProducer("P2", 0, 1, 16, 9000, buf, 6, 3),
            ProcessControlBlock.createConsumer("C1", 0, 1, 16, 9000, buf, 6, 2),
            ProcessControlBlock.createConsumer("C2", 0, 1, 16, 9000, buf, 6, 2),
        };
        for (ProcessControlBlock p : ps) {
            cl.submit(p);
        }
        int full = 0;
        int empty = 0;
        int mutex = 0;
        com.avilaos.enums.BlockReason[] last = new com.avilaos.enums.BlockReason[ps.length];
        int cycle = 0;
        while (cycle < MAX_CYCLES && !done(ps)) {
            cycle++;
            tick(cl, cycle);
            for (int i = 0; i < ps.length; i++) {
                com.avilaos.enums.BlockReason r = ps[i].getBlockReason();
                if (r != last[i]) {                // cuenta cada bloqueo una vez (al entrar)
                    switch (r) {
                        case BUFFER_FULL -> full++;
                        case BUFFER_EMPTY -> empty++;
                        case SEMAPHORE_MUTEX -> mutex++;
                        default -> { }
                    }
                }
                last[i] = r;
            }
        }
        GlobalMetrics g = new MetricsCollector(cl).computeGlobal();
        BufferStatus st = buf.snapshot();
        if (st.produced() != 12 || st.consumed() != 12) {
            System.out.println("ERROR: el buffer no cuadra");
        }
        System.out.printf("| %d | %d | %.2f | %d | %d | %d |%n", capacity, cycle, g.avgSemaphoreBlocked(), full, empty, mutex);
    }

    // ===================================================================== auxiliares

    private static void tick(Cluster cl, int cycle) {
        for (int i = 0; i < cl.getComputerCount(); i++) {
            cl.getComputer(i).onTick(cycle);
        }
    }

    private static boolean allSubmitted(ProcessControlBlock[] ps) {
        for (ProcessControlBlock p : ps) {
            if (p == null) {
                return false;
            }
        }
        return true;
    }

    private static boolean done(ProcessControlBlock... ps) {
        for (ProcessControlBlock p : ps) {
            if (p == null || p.getState() != ProcessState.TERMINATED) {
                return false;
            }
        }
        return true;
    }
}

