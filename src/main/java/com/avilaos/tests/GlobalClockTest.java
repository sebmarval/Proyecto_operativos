/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.tests;

import com.avilaos.enums.ProcessType;
import com.avilaos.enums.SchedulingPolicy;
import com.avilaos.hardware.ClockListener;
import com.avilaos.hardware.GlobalClock;
import com.avilaos.kernel.EventLogger;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.system.Computer;
import java.util.concurrent.Semaphore;

/**
 * Prueba manual del reloj global con hilos reales.
 * Ejecutar en NetBeans: clic derecho sobre este archivo -> Run File (Shift+F6).
 *
 * @author SebMarval
 */
public class GlobalClockTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws InterruptedException {
        testLockstep();
        testPauseAndSpeed();
        testTwoComputersRunProcesses();
        testValidation();
        demoTwoComputers();
        System.out.println("--------------------------------");
        System.out.println("Pruebas OK: " + passed + " | Fallidas: " + failed);
    }

    /**
     * 3 componentes en 3 hilos. Cada uno registra el último ciclo que ejecutó.
     * Si alguno se adelantara más de 1 ciclo respecto de otro, la barrera falló.
     */
    private static void testLockstep() throws InterruptedException {
        final int n = 3;
        final int[] lastCycle = new int[n];
        final int[] executions = new int[n];
        final String[] threadNames = new String[n];
        final boolean[] violation = {false};
        final Semaphore mutex = new Semaphore(1);

        ClockListener[] components = new ClockListener[n];
        for (int i = 0; i < n; i++) {
            final int me = i;
            components[i] = cycle -> {
                mutex.acquireUninterruptibly();
                try {
                    for (int other = 0; other < n; other++) {
                        if (other != me && (lastCycle[other] < cycle - 1 || lastCycle[other] > cycle)) {
                            violation[0] = true;
                        }
                    }
                    lastCycle[me] = cycle;
                    executions[me]++;
                    threadNames[me] = Thread.currentThread().getName();
                } finally {
                    mutex.release();
                }
            };
        }

        GlobalClock clock = new GlobalClock(components, 0);   // 0 ms = lo más rápido posible
        clock.start();
        Thread.sleep(300);
        clock.stop();

        int cycles = clock.getCurrentCycle();
        check("el reloj avanzó (" + cycles + " ciclos)", cycles > 10);
        check("ningún hilo se adelantó a los demás", !violation[0]);
        boolean exactlyOnePerTick = true;
        for (int i = 0; i < n; i++) {
            if (executions[i] != cycles) {
                exactlyOnePerTick = false;
            }
        }
        check("cada componente ejecutó exactamente 1 ciclo por tick", exactlyOnePerTick);
        check("cada componente corrió en su propio hilo",
                "Computer-0".equals(threadNames[0]) && "Computer-2".equals(threadNames[2]));
    }

    private static void testPauseAndSpeed() throws InterruptedException {
        ClockListener idle = cycle -> { };
        GlobalClock clock = new GlobalClock(new ClockListener[]{idle}, 10);
        clock.start();
        Thread.sleep(150);
        clock.pause();
        Thread.sleep(100);                       // da tiempo a que termine el tick en curso
        int frozen = clock.getCurrentCycle();
        Thread.sleep(200);
        check("en pausa el ciclo no avanza", clock.getCurrentCycle() == frozen);

        clock.resume();
        clock.setCycleDurationMs(1);             // cambio de velocidad en ejecución
        Thread.sleep(200);
        check("al reanudar y acelerar vuelve a avanzar", clock.getCurrentCycle() > frozen);
        clock.stop();
    }

    /** Dos computadores reales: cada uno termina su proceso en paralelo, en el mismo ciclo. */
    private static void testTwoComputersRunProcesses() throws InterruptedException {
        Computer pc0 = new Computer(0, 1024, SchedulingPolicy.FCFS, 2, EventLogger.SILENT);
        Computer pc1 = new Computer(1, 1024, SchedulingPolicy.FCFS, 2, EventLogger.SILENT);
        ProcessControlBlock a = new ProcessControlBlock("A", 0, ProcessType.CPU_BOUND, 1, 10, 500, 20);
        ProcessControlBlock b = new ProcessControlBlock("B", 1, ProcessType.CPU_BOUND, 1, 10, 500, 20);
        pc0.submit(a);
        pc1.submit(b);

        GlobalClock clock = new GlobalClock(new ClockListener[]{pc0, pc1}, 0);
        final int[] observed = {0};
        clock.setAfterTickObserver(cycle -> observed[0] = cycle);
        clock.start();
        Thread.sleep(300);
        clock.stop();

        check("los dos procesos terminaron", a.isFinished() && b.isFinished());
        check("ambos terminaron en el mismo ciclo (ejecución paralela)",
                a.getFinishCycle() == 20 && b.getFinishCycle() == 20);
        check("ambos kernels van en el mismo ciclo que el reloj",
                pc0.getKernel().getCurrentCycle() == clock.getCurrentCycle()
                && pc1.getKernel().getCurrentCycle() == clock.getCurrentCycle());
        check("el observador recibe el aviso de fin de ciclo", observed[0] == clock.getCurrentCycle());
    }

    private static void testValidation() {
        ClockListener idle = cycle -> { };
        check("rechaza duración negativa", throwsIAE(() -> new GlobalClock(new ClockListener[]{idle}, -1)));
        check("rechaza reloj sin componentes", throwsIAE(() -> new GlobalClock(new ClockListener[0], 10)));
    }

    /** Demo visible: 2 computadores, ciclos de 150 ms, log de eventos en consola. */
    private static void demoTwoComputers() throws InterruptedException {
        System.out.println();
        System.out.println("=== Demo: 2 computadores, ciclo de 150 ms ===");
        Computer pc0 = new Computer(0, 64, SchedulingPolicy.FCFS, 2, EventLogger.CONSOLE);
        Computer pc1 = new Computer(1, 64, SchedulingPolicy.FCFS, 2, EventLogger.CONSOLE);  
        pc0.submit(new ProcessControlBlock("Editor", 0, ProcessType.CPU_BOUND, 1, 32, 50, 3));
        ProcessControlBlock io = new ProcessControlBlock("Descarga", 1, ProcessType.IO_BOUND, 1, 16, 50, 3);
        io.configureIO(1, 2);
        pc1.submit(io);
        pc1.submit(new ProcessControlBlock("Compilador", 1, ProcessType.CPU_BOUND, 1, 40, 50, 2));

        GlobalClock clock = new GlobalClock(new ClockListener[]{pc0, pc1}, 150);
        clock.start();
        Thread.sleep(150 * 9);
        clock.stop();
        System.out.println("=== fin de la demo (" + clock.getCurrentCycle() + " ciclos) ===");
        System.out.println();
    }

    // ---------------------------------------------------------------- auxiliares

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

