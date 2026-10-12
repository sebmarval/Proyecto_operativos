/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.hardware;

import java.util.concurrent.Semaphore;

/**
 * Reloj global del clúster. Corre en su propio hilo de Java.
 *
 * En cada tick:
 *  1. Avanza el número de ciclo.
 *  2. Autoriza a TODOS los computadores (cada uno en su hilo ClockWorker).
 *  3. Espera con tickDone.acquire(N) a que los N terminen su ciclo (barrera).
 *  4. Avisa al observador (GUI / métricas) que el ciclo completo terminó.
 *  5. Duerme la duración del ciclo (ms, configurable en ejecución).
 *
 * Garantía: ningún computador ejecuta el ciclo k+1 hasta que todos terminaron
 * el ciclo k. Los milisegundos solo controlan la velocidad visual; la lógica
 * del sistema siempre cuenta ciclos.
 *
 * @author SebMarval
 */
public final class GlobalClock implements Runnable {

    public static final int MAX_CYCLE_DURATION_MS = 10_000;

    private final ClockWorker[] workers;
    private final Semaphore tickDone = new Semaphore(0);
    private final Thread clockThread;

    private volatile int cycleDurationMs;
    private volatile int currentCycle = 0;
    private volatile boolean running = false;
    private volatile boolean paused = false;
    private volatile ClockListener afterTickObserver;

    /**
     * @param components      los computadores (uno o más)
     * @param cycleDurationMs duración de cada ciclo en milisegundos
     */
    public GlobalClock(ClockListener[] components, int cycleDurationMs) {
        if (components == null || components.length == 0) {
            throw new IllegalArgumentException("El reloj necesita al menos un componente");
        }
        setCycleDurationMs(cycleDurationMs);
        this.workers = new ClockWorker[components.length];
        for (int i = 0; i < components.length; i++) {
            workers[i] = new ClockWorker(components[i], tickDone, "Computer-" + i);
        }
        this.clockThread = new Thread(this, "GlobalClock");
        this.clockThread.setDaemon(true);
    }

    // ================================================================ ciclo de vida

    /** Arranca los hilos de los computadores y el hilo del reloj. */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        for (ClockWorker worker : workers) {
            worker.start();
        }
        clockThread.start();
    }

    /** Detiene el reloj y luego los hilos de los computadores. */
    public void stop() {
        running = false;
        clockThread.interrupt();       // por si está durmiendo entre ciclos
        try {
            clockThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (ClockWorker worker : workers) {
            worker.stop();
        }
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    @Override
    public void run() {
        while (running) {
            if (paused) {
                sleepQuietly(50);
                continue;
            }
            tick();
            sleepQuietly(cycleDurationMs);
        }
    }

    /** Un tick completo: autoriza a todos y espera a todos (barrera). */
    private void tick() {
        int cycle = currentCycle + 1;
        for (ClockWorker worker : workers) {
            worker.startTick(cycle);
        }
        tickDone.acquireUninterruptibly(workers.length);
        currentCycle = cycle;
        ClockListener observer = afterTickObserver;
        if (observer != null) {
            observer.onTick(cycle);
        }
    }

    private void sleepQuietly(int ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            // stop() interrumpe el sueño; el while revisa 'running'
        }
    }

    // ================================================================ configuración

    /** Cambia la duración del ciclo; se puede llamar con la simulación corriendo. */
    public final void setCycleDurationMs(int ms) {
        if (ms < 0 || ms > MAX_CYCLE_DURATION_MS) {
            throw new IllegalArgumentException(
                    "La duración del ciclo debe estar entre 0 y " + MAX_CYCLE_DURATION_MS + " ms");
        }
        this.cycleDurationMs = ms;
    }

    /** Se llama al final de cada tick, cuando TODOS los computadores terminaron. */
    public void setAfterTickObserver(ClockListener observer) {
        this.afterTickObserver = observer;
    }

    // ================================================================ consultas

    public int getCurrentCycle()     { return currentCycle; }
    public int getCycleDurationMs()  { return cycleDurationMs; }
    public boolean isRunning()       { return running; }
    public boolean isPaused()        { return paused; }
    public int getComponentCount()   { return workers.length; }
}

