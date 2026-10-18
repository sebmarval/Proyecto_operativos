/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.tests;

import com.avilaos.enums.BlockReason;
import com.avilaos.enums.MicroInstruction;
import com.avilaos.enums.ProcessState;
import com.avilaos.enums.ProcessType;
import com.avilaos.enums.SchedulingPolicy;
import com.avilaos.kernel.EventLogger;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.sync.BoundedBuffer;
import com.avilaos.sync.BufferStatus;
import com.avilaos.system.Computer;

/**
 * Prueba del productor-consumidor LOCAL (un computador) y de la planificación
 * integrada en el kernel.
 * Ejecutar en NetBeans: clic derecho sobre este archivo -> Run File (Shift+F6).
 *
 * @author SebMarval
 */
public class ProducerConsumerTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testInstructionCountAndProgram();
        testBufferRam();
        testProducerBlocksOnFullBuffer();
        testRoundRobinMutexContention();
        testDeadlineWhileHoldingMutex();
        testPriorityPreemption();
        testRoundRobinAndPolicySwitch();
        demo();
        System.out.println("--------------------------------");
        System.out.println("Pruebas OK: " + passed + " | Fallidas: " + failed);
    }

    /** D5: instrucciones = elementos x (trabajo + 5), y el micro-programa en el orden de Stallings. */
    private static void testInstructionCountAndProgram() {
        BoundedBuffer buf = new BoundedBuffer("B", 2, 0);
        ProcessControlBlock prod = ProcessControlBlock.createProducer("prod", 0, 1, 10, 100, buf, 2, 1);
        check("productor: 2 x (1 + 5) = 12 instrucciones", prod.getTotalInstructions() == 12);

        MicroInstruction[] expected = {
            MicroInstruction.USER_WORK, MicroInstruction.WAIT_EMPTY, MicroInstruction.WAIT_MUTEX,
            MicroInstruction.ADD_ITEM, MicroInstruction.SIGNAL_MUTEX, MicroInstruction.SIGNAL_FULL};
        boolean ok = true;
        prod.setState(ProcessState.RUNNING);
        for (MicroInstruction m : expected) {
            if (prod.currentInstruction() != m) {
                ok = false;
            }
            prod.executeInstruction();      // solo avanza el PC (sin kernel)
        }
        check("micro-programa del productor en orden", ok);

        ProcessControlBlock cons = ProcessControlBlock.createConsumer("cons", 0, 1, 10, 100, buf, 1, 2);
        check("consumidor empieza con semWait(n)", cons.currentInstruction() == MicroInstruction.WAIT_FULL);
        check("rechaza productor sin buffer", throwsIAE(() ->
                ProcessControlBlock.createProducer("x", 0, 1, 10, 100, null, 1, 1)));
    }

    private static void testBufferRam() {
        Computer pc = new Computer(0, 20, SchedulingPolicy.FCFS, 2, EventLogger.SILENT);
        pc.getKernel().hostBuffer(new BoundedBuffer("B", 15, 0));
        check("el buffer ocupa RAM en su anfitrión", pc.getKernel().getRam().getUsed() == 15);
        check("rechaza buffer sin RAM suficiente",
                throwsIAE(() -> pc.getKernel().hostBuffer(new BoundedBuffer("B2", 10, 0))));
    }

    /** Buffer de capacidad 1: el productor se bloquea (BUFFER_FULL) hasta que el consumidor extrae. */
    private static void testProducerBlocksOnFullBuffer() {
        Computer pc = new Computer(0, 100, SchedulingPolicy.FCFS, 2, EventLogger.SILENT);
        BoundedBuffer buf = new BoundedBuffer("B", 1, 0);
        pc.getKernel().hostBuffer(buf);
        ProcessControlBlock prod = ProcessControlBlock.createProducer("prod", 0, 1, 10, 500, buf, 3, 1);
        ProcessControlBlock cons = ProcessControlBlock.createConsumer("cons", 0, 1, 10, 500, buf, 3, 1);
        pc.submit(prod);
        pc.submit(cons);

        boolean sawFullBlock = false;
        for (int cycle = 1; cycle <= 200 && !(isDone(prod) && isDone(cons)); cycle++) {
            pc.onTick(cycle);
            if (prod.getState() == ProcessState.BLOCKED && prod.getBlockReason() == BlockReason.BUFFER_FULL) {
                sawFullBlock = true;
            }
        }
        BufferStatus st = buf.snapshot();
        check("el productor se bloqueó con el buffer lleno", sawFullBlock);
        check("ambos terminan sin vencer deadline", isDone(prod) && isDone(cons)
                && !prod.isDeadlineMissed() && !cons.isDeadlineMissed());
        check("3 producidos y 3 consumidos", st.produced() == 3 && st.consumed() == 3
                && prod.getItemsProcessed() == 3 && cons.getItemsProcessed() == 3);
        check("semáforos vuelven a s=1, n=0, e=1", st.valueS() == 1 && st.valueN() == 0 && st.valueE() == 1);
        check("se contó tiempo bloqueado en semáforos", prod.getSemaphoreBlockedCycles() > 0);
    }

    /**
     * Round Robin con quantum 1: los procesos son expropiados a mitad de su
     * sección crítica, así que otros chocan con el mutex s. Al final todo debe cuadrar.
     */
    private static void testRoundRobinMutexContention() {
        Computer pc = new Computer(0, 100, SchedulingPolicy.ROUND_ROBIN, 1, EventLogger.SILENT);
        BoundedBuffer buf = new BoundedBuffer("B", 2, 0);
        pc.getKernel().hostBuffer(buf);
        ProcessControlBlock p1 = ProcessControlBlock.createProducer("p1", 0, 1, 5, 2000, buf, 4, 1);
        ProcessControlBlock p2 = ProcessControlBlock.createProducer("p2", 0, 1, 5, 2000, buf, 4, 2);
        ProcessControlBlock c1 = ProcessControlBlock.createConsumer("c1", 0, 1, 5, 2000, buf, 8, 1);
        pc.submit(p1);
        pc.submit(p2);
        pc.submit(c1);

        boolean sawMutexBlock = false;
        for (int cycle = 1; cycle <= 500 && !(isDone(p1) && isDone(p2) && isDone(c1)); cycle++) {
            pc.onTick(cycle);
            sawMutexBlock |= p1.getBlockReason() == BlockReason.SEMAPHORE_MUTEX
                    || p2.getBlockReason() == BlockReason.SEMAPHORE_MUTEX
                    || c1.getBlockReason() == BlockReason.SEMAPHORE_MUTEX;
        }
        BufferStatus st = buf.snapshot();
        check("con RR quantum 1 algún proceso se bloquea en el mutex s", sawMutexBlock);
        check("2 productores + 1 consumidor terminan", isDone(p1) && isDone(p2) && isDone(c1));
        check("8 producidos = 8 consumidos y semáforos íntegros", st.produced() == 8 && st.consumed() == 8
                && st.valueS() == 1 && st.valueN() == 0 && st.valueE() == 2);
    }

    /**
     * Un productor muere (deadline) justo después de tomar el mutex s.
     * El kernel debe devolver s y e: si no, el buffer quedaría trabado para siempre.
     */
    private static void testDeadlineWhileHoldingMutex() {
        Computer pc = new Computer(0, 100, SchedulingPolicy.FCFS, 2, EventLogger.SILENT);
        BoundedBuffer buf = new BoundedBuffer("B", 2, 0);
        pc.getKernel().hostBuffer(buf);
        // ciclos: 1 trabajo, 2 semWait(e), 3 semWait(s) -> deadline 3 vence al final del ciclo 3
        ProcessControlBlock doomed = ProcessControlBlock.createProducer("doomed", 0, 1, 10, 3, buf, 1, 1);
        pc.submit(doomed);
        for (int cycle = 1; cycle <= 3; cycle++) {
            pc.onTick(cycle);
        }
        BufferStatus st = buf.snapshot();
        check("el productor murió por deadline", doomed.isDeadlineMissed()
                && doomed.getState() == ProcessState.TERMINATED);
        check("el kernel liberó s y devolvió el espacio (s=1, e=2)", st.valueS() == 1 && st.valueE() == 2
                && st.valueN() == 0);

        ProcessControlBlock cons = ProcessControlBlock.createConsumer("cons", 0, 1, 10, 100, buf, 1, 1);
        ProcessControlBlock prod = ProcessControlBlock.createProducer("prod", 0, 1, 10, 100, buf, 1, 1);
        pc.submit(cons);
        pc.submit(prod);
        for (int cycle = 4; cycle <= 60 && !(isDone(cons) && isDone(prod)); cycle++) {
            pc.onTick(cycle);
        }
        check("el buffer sigue usable después", isDone(cons) && isDone(prod) && !cons.isDeadlineMissed());
    }

    /** Prioridades: un proceso más prioritario que llega después expropia al que corre. */
    private static void testPriorityPreemption() {
        Computer pc = new Computer(0, 100, SchedulingPolicy.PREEMPTIVE_PRIORITY, 2, EventLogger.SILENT);
        ProcessControlBlock low = cpu("bajo", 5, 10);
        pc.submit(low);
        pc.onTick(1);
        pc.onTick(2);
        ProcessControlBlock high = cpu("alto", 1, 2);
        pc.submit(high);
        pc.onTick(3);
        check("el de mayor prioridad expropia al llegar",
                high.getState() == ProcessState.RUNNING && low.getState() == ProcessState.READY);
        pc.onTick(4);
        pc.onTick(5);
        check("al terminar el prioritario, vuelve el otro", high.isFinished()
                && low.getState() == ProcessState.RUNNING);
    }

    private static void testRoundRobinAndPolicySwitch() {
        Computer pc = new Computer(0, 100, SchedulingPolicy.ROUND_ROBIN, 2, EventLogger.SILENT);
        ProcessControlBlock a = cpu("A", 1, 10);
        ProcessControlBlock b = cpu("B", 1, 10);
        pc.submit(a);
        pc.submit(b);
        pc.onTick(1);
        pc.onTick(2);
        pc.onTick(3);
        check("RR: tras 2 ciclos (quantum) cambia de proceso",
                b.getState() == ProcessState.RUNNING && a.getProgramCounter() == 2);

        pc.getKernel().setSchedulingPolicy(SchedulingPolicy.FCFS, 2);
        for (int cycle = 4; cycle <= 9; cycle++) {
            pc.onTick(cycle);
        }
        check("cambio a FCFS en ejecución: B ya no es expropiado",
                pc.getKernel().getSchedulingPolicy() == SchedulingPolicy.FCFS
                && b.getState() == ProcessState.RUNNING && b.getProgramCounter() == 7);
        check("rechaza quantum inválido", throwsIAE(() ->
                pc.getKernel().setSchedulingPolicy(SchedulingPolicy.ROUND_ROBIN, 0)));
    }

    private static void demo() {
        System.out.println();
        System.out.println("=== Demo: productor y consumidor, buffer de capacidad 1 ===");
        Computer pc = new Computer(0, 100, SchedulingPolicy.FCFS, 2, EventLogger.CONSOLE);
        BoundedBuffer buf = new BoundedBuffer("Buzon", 1, 0);
        pc.getKernel().hostBuffer(buf);
        ProcessControlBlock prod = ProcessControlBlock.createProducer("Productor", 0, 1, 10, 100, buf, 2, 1);
        ProcessControlBlock cons = ProcessControlBlock.createConsumer("Consumidor", 0, 1, 10, 100, buf, 2, 1);
        pc.submit(prod);
        pc.submit(cons);
        for (int cycle = 1; cycle <= 40 && !(isDone(prod) && isDone(cons)); cycle++) {
            pc.onTick(cycle);
        }
        System.out.println("Estado final: " + buf);
        System.out.println("=== fin de la demo ===");
        System.out.println();
    }

    // ---------------------------------------------------------------- auxiliares

    private static boolean isDone(ProcessControlBlock p) {
        return p.getState() == ProcessState.TERMINATED;
    }

    private static ProcessControlBlock cpu(String name, int priority, int instructions) {
        return new ProcessControlBlock(name, 0, ProcessType.CPU_BOUND, priority, 10, 500, instructions);
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
