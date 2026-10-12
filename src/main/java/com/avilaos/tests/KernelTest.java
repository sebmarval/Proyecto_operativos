/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.tests;

import com.avilaos.enums.BlockReason;
import com.avilaos.enums.ProcessState;
import com.avilaos.enums.ProcessType;
import com.avilaos.kernel.EventLogger;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.system.Computer;

/**
 * Prueba manual de un computador completo (kernel + CPU + RAM), avanzando
 * los ciclos a mano (el reloj global con hilos llega en el Paso 5).
 * Ejecutar en NetBeans: clic derecho sobre este archivo -> Run File (Shift+F6).
 *
 * @author SebMarval
 */
public class KernelTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testAdmissionByMemory();
        testIOBound();
        testDeadlineExpiry();
        testValidation();
        demoWithLog();
        System.out.println("--------------------------------");
        System.out.println("Pruebas OK: " + passed + " | Fallidas: " + failed);
    }

    /** RAM 100: P1 (60) entra; P2 (60) espera en NEW hasta que P1 termina y libera. */
    private static void testAdmissionByMemory() {
        Computer pc = new Computer(0, 100, EventLogger.SILENT);
        ProcessControlBlock p1 = cpuBound("P1", 0, 60, 3);
        ProcessControlBlock p2 = cpuBound("P2", 0, 60, 2);
        pc.submit(p1);
        pc.submit(p2);

        pc.onTick(1);
        check("P1 admitido y ejecutando", p1.getState() == ProcessState.RUNNING);
        check("P2 sigue en NEW por falta de RAM", p2.getState() == ProcessState.NEW);
        check("RAM usada = 60", pc.getKernel().getRam().getUsed() == 60);

        pc.onTick(2);
        pc.onTick(3);
        check("P1 termina en el ciclo 3 y libera RAM",
                p1.getState() == ProcessState.TERMINATED && p1.getFinishCycle() == 3);

        pc.onTick(4);
        check("P2 admitido en el ciclo siguiente", p2.getState() == ProcessState.RUNNING
                && pc.getKernel().getRam().getUsed() == 60);
        pc.onTick(5);
        check("P2 termina y la RAM queda libre", p2.getState() == ProcessState.TERMINATED
                && pc.getKernel().getRam().getUsed() == 0);
        check("2 procesos completados", pc.getKernel().getCompletedCount() == 2);
        check("utilización 100% (5 de 5 ciclos)", pc.getKernel().getCpu().getUtilizationPercent() == 100.0);
    }

    /** I/O bound: cada 2 instrucciones pide E/S de 3 ciclos; otro proceso usa la CPU mientras tanto. */
    private static void testIOBound() {
        Computer pc = new Computer(0, 100, EventLogger.SILENT);
        ProcessControlBlock io = new ProcessControlBlock("IO", 0, ProcessType.IO_BOUND, 1, 10, 100, 4);
        io.configureIO(2, 3);
        ProcessControlBlock cpu = cpuBound("CPU", 0, 10, 10);
        pc.submit(io);
        pc.submit(cpu);

        pc.onTick(1);
        pc.onTick(2);
        check("IO bound se bloquea tras 2 instrucciones",
                io.getState() == ProcessState.BLOCKED && io.getBlockReason() == BlockReason.IO_WAIT);

        pc.onTick(3);
        check("el proceso CPU bound aprovecha la CPU", cpu.getState() == ProcessState.RUNNING);

        pc.onTick(4);
        pc.onTick(5);
        check("tras 3 ciclos de E/S vuelve a READY", io.getState() == ProcessState.READY);
    }

    /** Un proceso en READY también vence su deadline y libera su RAM. */
    private static void testDeadlineExpiry() {
        Computer pc = new Computer(0, 100, EventLogger.SILENT);
        ProcessControlBlock longJob = cpuBound("largo", 0, 30, 10);
        ProcessControlBlock waiting = new ProcessControlBlock("espera", 0, ProcessType.CPU_BOUND, 1, 40, 2, 5);
        pc.submit(longJob);
        pc.submit(waiting);

        pc.onTick(1);
        check("el segundo espera en READY", waiting.getState() == ProcessState.READY);
        pc.onTick(2);
        check("vence su deadline sin haber ejecutado",
                waiting.getState() == ProcessState.TERMINATED && waiting.isDeadlineMissed());
        check("libera su RAM (solo queda la del largo)", pc.getKernel().getRam().getUsed() == 30);
        check("contador de deadlines incumplidos", pc.getKernel().getDeadlineMissedCount() == 1);
    }

    private static void testValidation() {
        Computer pc = new Computer(0, 100, EventLogger.SILENT);
        check("rechaza proceso de otro computador", throwsIAE(() -> pc.submit(cpuBound("x", 1, 10, 5))));
        check("rechaza proceso más grande que la RAM total", throwsIAE(() -> pc.submit(cpuBound("x", 0, 150, 5))));
    }

    /** Muestra el log de eventos de una corrida corta (para ver el flujo). */
    private static void demoWithLog() {
        System.out.println();
        System.out.println("=== Demo con log de eventos (RAM 50) ===");
        Computer pc = new Computer(0, 50, EventLogger.CONSOLE);
        ProcessControlBlock a = cpuBound("A", 0, 30, 2);
        ProcessControlBlock b = new ProcessControlBlock("B", 0, ProcessType.IO_BOUND, 1, 20, 50, 3);
        b.configureIO(1, 2);
        ProcessControlBlock c = cpuBound("C", 0, 40, 1);
        pc.submit(a);
        pc.submit(b);
        pc.submit(c);
        for (int cycle = 1; cycle <= 9; cycle++) {
            pc.onTick(cycle);
        }
        System.out.println("=== fin de la demo ===");
        System.out.println();
    }

    // ---------------------------------------------------------------- auxiliares

    private static ProcessControlBlock cpuBound(String name, int computer, int memory, int instructions) {
        return new ProcessControlBlock(name, computer, ProcessType.CPU_BOUND, 1, memory, 100, instructions);
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
