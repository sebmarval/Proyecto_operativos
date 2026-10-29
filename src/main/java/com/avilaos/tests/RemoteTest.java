/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.tests;

import com.avilaos.enums.BlockReason;
import com.avilaos.enums.ProcessState;
import com.avilaos.enums.ProcessType;
import com.avilaos.enums.SchedulingPolicy;
import com.avilaos.kernel.EventLogger;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.sync.BoundedBuffer;
import com.avilaos.sync.BufferStatus;
import com.avilaos.system.Cluster;

/**
 * Prueba del clúster: buffers compartidos entre computadores, acceso remoto
 * con latencia de red y despertares entre computadores.
 * Ejecutar en NetBeans: clic derecho sobre este archivo -> Run File (Shift+F6).
 *
 * @author SebMarval
 */
public class RemoteTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws InterruptedException {
        testClusterValidation();
        testAutomaticAssignment();
        testLatencyTiming();
        testRemoteProducerConsumer();
        testLatencyCostsTime();
        testDeterministicWakeUps();
        testWithRealThreads();
        demo();
        System.out.println("--------------------------------");
        System.out.println("Pruebas OK: " + passed + " | Fallidas: " + failed);
    }

    private static void testClusterValidation() {
        check("rechaza clúster de 1 computador",
                throwsIAE(() -> new Cluster(new int[]{100}, SchedulingPolicy.FCFS, 2, 0, 0, null)));
        Cluster cl = cluster(0);
        cl.createBuffer("B", 4, 0);
        check("el buffer ocupa RAM solo en su anfitrión",
                cl.getComputer(0).getKernel().getRam().getUsed() == 4
                && cl.getComputer(1).getKernel().getRam().getUsed() == 0);
        check("rechaza id de buffer repetido", throwsIAE(() -> cl.createBuffer("B", 2, 1)));
        check("rechaza anfitrión inexistente", throwsIAE(() -> cl.createBuffer("C", 2, 7)));
        check("rechaza latencia negativa", throwsIAE(() -> cl.setNetworkLatency(-1)));
        check("busca buffers por id", cl.getBuffer("B") != null && cl.getBuffer("X") == null);
    }

    private static void testAutomaticAssignment() {
        Cluster cl = cluster(0);
        cl.submit(cpu("a", 0));
        cl.submit(cpu("b", 0));
        check("automático elige el computador con menos procesos", cl.pickComputerAutomatically() == 1);

        Cluster mixed = new Cluster(new int[]{256, 64}, SchedulingPolicy.FCFS, 2, 0, 0, EventLogger.SILENT);
        mixed.submit(cpu("c", 0));
        check("automático descarta computadores sin RAM suficiente (100 -> PC0 aunque tenga más carga)",
                mixed.pickComputerAutomatically(100) == 0 && mixed.pickComputerAutomatically(10) == 1);
        check("automático rechaza si ningún computador tiene RAM suficiente",
                throwsIAE(() -> mixed.pickComputerAutomatically(500)));
    }

    /**
     * T09: latencia 3. Un consumidor del PC1 usa un buffer del PC0.
     * Ciclo 1: pide el acceso remoto y se bloquea (NETWORK_LATENCY).
     * Ciclo 4: vuelve y ejecuta su semWait(n): el acceso se completa 3 ciclos después.
     */
    private static void testLatencyTiming() {
        Cluster cl = cluster(3);
        BoundedBuffer buf = cl.createBuffer("B", 2, 0);
        ProcessControlBlock cons = ProcessControlBlock.createConsumer("remoto", 1, 1, 10, 500, buf, 1, 1);
        cl.submit(cons);

        tick(cl, 1);
        check("ciclo 1: bloqueado por latencia sin avanzar el PC",
                cons.getState() == ProcessState.BLOCKED
                && cons.getBlockReason() == BlockReason.NETWORK_LATENCY && cons.getProgramCounter() == 0);
        tick(cl, 2);
        tick(cl, 3);
        check("ciclos 2-3: sigue esperando la red", cons.getBlockReason() == BlockReason.NETWORK_LATENCY);
        tick(cl, 4);
        check("ciclo 4: ejecuta semWait(n) y se bloquea porque el buffer está vacío",
                cons.getProgramCounter() == 1 && cons.getBlockReason() == BlockReason.BUFFER_EMPTY);
    }

    /** Productor local (PC0) y consumidor remoto (PC1): el despertar cruza de computador. */
    private static void testRemoteProducerConsumer() {
        Cluster cl = cluster(2);
        BoundedBuffer buf = cl.createBuffer("B", 1, 0);
        ProcessControlBlock prod = ProcessControlBlock.createProducer("prod", 0, 1, 10, 2000, buf, 4, 1);
        ProcessControlBlock cons = ProcessControlBlock.createConsumer("cons", 1, 1, 10, 2000, buf, 4, 1);
        cl.submit(prod);
        cl.submit(cons);
        boolean prodPaidLatency = false;
        boolean consPaidLatency = false;
        int cycle = 1;
        while (cycle <= 500 && !(done(prod) && done(cons))) {
            tick(cl, cycle++);
            prodPaidLatency |= prod.getBlockReason() == BlockReason.NETWORK_LATENCY;
            consPaidLatency |= cons.getBlockReason() == BlockReason.NETWORK_LATENCY;
        }
        BufferStatus st = buf.snapshot();
        check("ambos terminan (despertares entre computadores funcionan)", done(prod) && done(cons)
                && !prod.isDeadlineMissed() && !cons.isDeadlineMissed());
        check("solo el remoto paga latencia", consPaidLatency && !prodPaidLatency);
        check("4 producidos = 4 consumidos, semáforos íntegros", st.produced() == 4 && st.consumed() == 4
                && st.valueS() == 1 && st.valueN() == 0 && st.valueE() == 1);
    }

    /** La misma carga tarda más con más latencia. */
    private static void testLatencyCostsTime() {
        int fast = runRemoteWorkload(0);
        int slow = runRemoteWorkload(4);
        check("con latencia 4 termina más tarde (" + fast + " vs " + slow + " ciclos)", slow > fast);
    }

    private static int runRemoteWorkload(int latency) {
        Cluster cl = cluster(latency);
        BoundedBuffer buf = cl.createBuffer("B", 2, 1);
        ProcessControlBlock prod = ProcessControlBlock.createProducer("prod", 0, 1, 10, 5000, buf, 3, 1);
        ProcessControlBlock cons = ProcessControlBlock.createConsumer("cons", 1, 1, 10, 5000, buf, 3, 1);
        cl.submit(prod);
        cl.submit(cons);
        int cycle = 1;
        while (cycle <= 1000 && !(done(prod) && done(cons))) {
            tick(cl, cycle++);
        }
        return Math.max(prod.getFinishCycle(), cons.getFinishCycle());
    }

    /**
     * Con hilos reales, en un mismo tick el PC0 puede terminar antes o después
     * que el PC1. El resultado NO debe depender de ese orden: se simulan ambos
     * órdenes y deben terminar en el mismo ciclo exacto.
     */
    private static void testDeterministicWakeUps() {
        int[] normal = runOrdered(false);
        int[] reversed = runOrdered(true);
        check("mismo resultado sin importar qué computador termina primero el tick ("
                + normal[0] + "/" + normal[1] + " vs " + reversed[0] + "/" + reversed[1] + ")",
                normal[0] == reversed[0] && normal[1] == reversed[1]);
    }

    private static int[] runOrdered(boolean reversed) {
        Cluster cl = cluster(1);
        BoundedBuffer buf = cl.createBuffer("B", 1, 0);
        ProcessControlBlock prod = ProcessControlBlock.createProducer("prod", 0, 1, 10, 2000, buf, 3, 1);
        ProcessControlBlock cons = ProcessControlBlock.createConsumer("cons", 1, 1, 10, 2000, buf, 3, 1);
        cl.submit(prod);
        cl.submit(cons);
        for (int cycle = 1; cycle <= 300 && !(done(prod) && done(cons)); cycle++) {
            if (reversed) {
                cl.getComputer(1).onTick(cycle);
                cl.getComputer(0).onTick(cycle);
            } else {
                tick(cl, cycle);
            }
        }
        return new int[]{prod.getFinishCycle(), cons.getFinishCycle()};
    }

    /** T10: 2 computadores en hilos reales con el reloj global, productores y consumidores cruzados. */
    private static void testWithRealThreads() throws InterruptedException {
        Cluster cl = new Cluster(new int[]{200, 200}, SchedulingPolicy.ROUND_ROBIN, 2, 0, 2, EventLogger.SILENT);
        BoundedBuffer b0 = cl.createBuffer("B0", 2, 0);
        BoundedBuffer b1 = cl.createBuffer("B1", 3, 1);
        ProcessControlBlock[] all = {
            ProcessControlBlock.createProducer("p0", 0, 1, 10, 9000, b0, 5, 1),
            ProcessControlBlock.createConsumer("c0", 1, 1, 10, 9000, b0, 5, 2),
            ProcessControlBlock.createProducer("p1", 0, 1, 10, 9000, b1, 6, 2),
            ProcessControlBlock.createConsumer("c1", 1, 1, 10, 9000, b1, 3, 1),
            ProcessControlBlock.createConsumer("c2", 0, 1, 10, 9000, b1, 3, 1),
            cpu("cpu", 1)
        };
        for (ProcessControlBlock p : all) {
            cl.submit(p);
        }
        cl.start();
        long limit = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < limit && !allDone(all)) {
            Thread.sleep(20);
        }
        cl.stop();
        BufferStatus s0 = b0.snapshot();
        BufferStatus s1 = b1.snapshot();
        check("hilos reales: todos los procesos terminan", allDone(all));
        check("hilos reales: B0 5/5 y B1 6/6 con semáforos íntegros",
                s0.produced() == 5 && s0.consumed() == 5 && s0.valueS() == 1 && s0.valueE() == 2
                && s1.produced() == 6 && s1.consumed() == 6 && s1.valueS() == 1 && s1.valueE() == 3);
    }

    private static void demo() {
        System.out.println();
        System.out.println("=== Demo: productor en PC0, consumidor en PC1, buffer en PC0, latencia 2 ===");
        Cluster cl = new Cluster(new int[]{64, 64}, SchedulingPolicy.FCFS, 2, 0, 2, EventLogger.CONSOLE);
        BoundedBuffer buf = cl.createBuffer("Canal", 1, 0);
        ProcessControlBlock prod = ProcessControlBlock.createProducer("Sensor", 0, 1, 8, 200, buf, 1, 1);
        ProcessControlBlock cons = ProcessControlBlock.createConsumer("Monitor", 1, 1, 8, 200, buf, 1, 1);
        cl.submit(prod);
        cl.submit(cons);
        int cycle = 1;
        while (cycle <= 60 && !(done(prod) && done(cons))) {
            tick(cl, cycle++);
        }
        System.out.println("=== fin de la demo ===");
        System.out.println();
    }

    // ---------------------------------------------------------------- auxiliares

    /** Un tick manual: cada computador ejecuta su ciclo (lo mismo que hace el reloj, sin hilos). */
    private static void tick(Cluster cl, int cycle) {
        for (int i = 0; i < cl.getComputerCount(); i++) {
            cl.getComputer(i).onTick(cycle);
        }
    }

    private static Cluster cluster(int latency) {
        return new Cluster(new int[]{100, 100}, SchedulingPolicy.FCFS, 2, 0, latency, EventLogger.SILENT);
    }

    private static ProcessControlBlock cpu(String name, int computer) {
        return new ProcessControlBlock(name, computer, ProcessType.CPU_BOUND, 1, 10, 9000, 5);
    }

    private static boolean done(ProcessControlBlock p) {
        return p.getState() == ProcessState.TERMINATED;
    }

    private static boolean allDone(ProcessControlBlock[] ps) {
        for (ProcessControlBlock p : ps) {
            if (!done(p)) {
                return false;
            }
        }
        return true;
    }

    private static boolean throwsIAE(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
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
