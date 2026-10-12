/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.tests;

import com.avilaos.enums.CPUMode;
import com.avilaos.enums.ProcessState;
import com.avilaos.enums.ProcessType;
import com.avilaos.hardware.CPU;
import com.avilaos.hardware.RAM;
import com.avilaos.model.ProcessControlBlock;

/**
 * Prueba manual de RAM y CPU.
 * Ejecutar en NetBeans: clic derecho sobre este archivo -> Run File (Shift+F6).
 *
 * @author SebMarval
 */
public class HardwareTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testRam();
        testCpu();
        System.out.println("--------------------------------");
        System.out.println("Pruebas OK: " + passed + " | Fallidas: " + failed);
    }

    private static void testRam() {
        RAM ram = new RAM(100);
        check("RAM nueva vacía", ram.getUsed() == 0 && ram.getFree() == 100);
        check("reserva si alcanza", ram.allocate(60) && ram.getFree() == 40);
        check("rechaza si no alcanza y no cambia", !ram.allocate(50) && ram.getUsed() == 60);
        check("reserva exacta hasta llenar", ram.allocate(40) && ram.getFree() == 0);
        ram.free(60);
        check("libera memoria", ram.getUsed() == 40 && ram.canAllocate(60));
        check("rechaza liberar más de lo usado", throwsIAE(() -> ram.free(41)));
        check("rechaza capacidad 0", throwsIAE(() -> new RAM(0)));
    }

    private static void testCpu() {
        CPU cpu = new CPU(0);
        cpu.executeCycle();
        check("CPU sin proceso: ciclo ocioso en modo SO",
                cpu.isIdle() && cpu.getIdleCycles() == 1 && cpu.getMode() == CPUMode.KERNEL_MODE);

        ProcessControlBlock p = new ProcessControlBlock("p", 0, ProcessType.CPU_BOUND, 1, 10, 100, 2);
        boolean rejected = false;
        try {
            cpu.dispatch(p, 1); // p sigue en NEW
        } catch (IllegalStateException e) {
            rejected = true;
        }
        check("no despacha un proceso que no está READY", rejected);

        p.setState(ProcessState.READY);
        cpu.dispatch(p, 5);
        check("despacho: proceso en RUNNING y primer ciclo registrado",
                p.getState() == ProcessState.RUNNING && p.getFirstRunCycle() == 5);

        cpu.executeCycle();
        check("ejecuta 1 instrucción en modo usuario",
                p.getProgramCounter() == 1 && cpu.getMode() == CPUMode.USER_MODE && cpu.getQuantumUsed() == 1);

        cpu.executeCycle();
        check("el proceso termina sus instrucciones", p.isFinished());

        ProcessControlBlock out = cpu.release();
        check("release devuelve el proceso y libera la CPU", out == p && cpu.isIdle() && cpu.getQuantumUsed() == 0);
        check("utilización = 2 ocupados de 3 ciclos", Math.abs(cpu.getUtilizationPercent() - 66.666) < 0.01);
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
