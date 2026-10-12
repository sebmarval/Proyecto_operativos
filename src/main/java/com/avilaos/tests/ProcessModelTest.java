/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.tests;

import com.avilaos.enums.BlockReason;
import com.avilaos.enums.ProcessState;
import com.avilaos.enums.ProcessType;
import com.avilaos.model.ProcessControlBlock;

/**
 * Prueba manual del modelo de procesos (PCB/TCB).
 * Ejecutar en NetBeans: clic derecho sobre este archivo -> Run File (Shift+F6).
 *
 * @author SebMarval
 */
public class ProcessModelTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws InterruptedException {
        testUniqueIds();
        testExecution();
        testDeadlineTicksInAnyState();
        testIOBound();
        testValidation();
        System.out.println("--------------------------------");
        System.out.println("Pruebas OK: " + passed + " | Fallidas: " + failed);
    }

    /** Dos hilos crean 500 procesos cada uno: ningún ID debe repetirse. */
    private static void testUniqueIds() throws InterruptedException {
        final int perThread = 500;
        final ProcessControlBlock[] created = new ProcessControlBlock[perThread * 2];
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < perThread; i++) {
                created[i] = newCpuProcess(10);
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < perThread; i++) {
                created[perThread + i] = newCpuProcess(10);
            }
        });
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        int maxId = 0;
        for (ProcessControlBlock p : created) {
            maxId = Math.max(maxId, p.getId());
        }
        boolean[] seen = new boolean[maxId + 1];
        boolean unique = true;
        for (ProcessControlBlock p : created) {
            if (seen[p.getId()]) {
                unique = false;
            }
            seen[p.getId()] = true;
        }
        check("IDs únicos con 2 hilos creando procesos", unique);
    }

    private static void testExecution() {
        ProcessControlBlock p = newCpuProcess(3);
        check("estado inicial NEW", p.getState() == ProcessState.NEW);

        boolean rejected = false;
        try {
            p.executeInstruction();
        } catch (IllegalStateException e) {
            rejected = true;
        }
        check("no ejecuta si no está en RUNNING", rejected);

        p.setState(ProcessState.RUNNING);
        p.executeInstruction();
        p.executeInstruction();
        check("PC y MAR avanzan +1 por instrucción",
                p.getProgramCounter() == 2 && p.getTcb().getMemoryAddressRegister() == 2);
        check("tiempo restante baja", p.getRemainingTime() == 1 && !p.isFinished());
        p.executeInstruction();
        check("termina al llegar a 0", p.isFinished() && p.getCpuCycles() == 3);
    }

    private static void testDeadlineTicksInAnyState() {
        ProcessControlBlock p = new ProcessControlBlock("dl", 0, ProcessType.CPU_BOUND, 1, 10, 2, 50);
        p.setState(ProcessState.READY);           // en cola de listos, sin ejecutar
        boolean expired1 = p.tickDeadline();
        boolean expired2 = p.tickDeadline();
        check("deadline baja aunque el proceso no ejecute", !expired1 && expired2);
        check("se marca como deadline incumplido", p.isDeadlineMissed());
    }

    private static void testIOBound() {
        ProcessControlBlock p = new ProcessControlBlock("io", 0, ProcessType.IO_BOUND, 1, 10, 100, 10);
        p.configureIO(3, 2);
        p.setState(ProcessState.RUNNING);
        p.executeInstruction();
        p.executeInstruction();
        check("no pide E/S antes de N instrucciones", !p.needsIO());
        p.executeInstruction();
        check("pide E/S al cumplir N instrucciones", p.needsIO());

        p.blockFor(BlockReason.IO_WAIT, p.getIoDurationCycles());
        check("queda BLOCKED por IO_WAIT",
                p.getState() == ProcessState.BLOCKED && p.getBlockReason() == BlockReason.IO_WAIT);
        p.accountCycle();
        boolean done1 = p.tickBlockTimer();
        boolean done2 = p.tickBlockTimer();
        check("la E/S dura M ciclos", !done1 && done2 && p.getBlockedCycles() == 1);

        p.setState(ProcessState.READY);
        check("al desbloquear se limpia el motivo", p.getBlockReason() == BlockReason.NONE && !p.needsIO());
    }

    private static void testValidation() {
        check("rechaza memoria 0", throwsIAE(() -> new ProcessControlBlock("x", 0, ProcessType.CPU_BOUND, 1, 0, 10, 10)));
        check("rechaza nombre vacío", throwsIAE(() -> new ProcessControlBlock("  ", 0, ProcessType.CPU_BOUND, 1, 5, 10, 10)));
        check("rechaza deadline 0", throwsIAE(() -> new ProcessControlBlock("x", 0, ProcessType.CPU_BOUND, 1, 5, 0, 10)));
    }

    // ---------------------------------------------------------------- auxiliares

    private static ProcessControlBlock newCpuProcess(int instructions) {
        return new ProcessControlBlock("cpu", 0, ProcessType.CPU_BOUND, 1, 10, 100, instructions);
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