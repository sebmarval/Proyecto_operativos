/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.hardware;

import java.util.concurrent.Semaphore;

/**
 * Hilo de Java que ejecuta a UN componente (un computador) al ritmo del reloj.
 *
 * Funcionamiento (barrera con Semaphores de Java):
 *  1. El hilo espera en 'tickStart' (empieza en 0 permisos, así que se bloquea).
 *  2. El reloj le da un permiso: el hilo ejecuta exactamente UN ciclo.
 *  3. Al terminar, libera un permiso en 'tickDone' (compartido con el reloj)
 *     y vuelve a esperar el siguiente tick.
 *
 * Así cada computador corre en su propio hilo, pero ninguno puede adelantarse:
 * avanza un ciclo solo cuando el reloj lo autoriza.
 *
 * @author SebMarval
 */
public final class ClockWorker implements Runnable {

    private final ClockListener component;
    private final Semaphore tickStart = new Semaphore(0);
    private final Semaphore tickDone;
    private final Thread thread;

    private int pendingCycle;          // visible al hilo gracias al release/acquire del semáforo
    private volatile boolean alive = true;

    ClockWorker(ClockListener component, Semaphore tickDone, String threadName) {
        this.component = component;
        this.tickDone = tickDone;
        this.thread = new Thread(this, threadName);
        this.thread.setDaemon(true);   // no impide cerrar la aplicación
    }

    void start() {
        thread.start();
    }

    /** Autoriza al hilo a ejecutar el ciclo indicado (lo llama el reloj). */
    void startTick(int cycle) {
        pendingCycle = cycle;
        tickStart.release();
    }

    /** Detiene el hilo de forma ordenada. */
    void stop() {
        alive = false;
        tickStart.release();           // lo despierta para que vea alive == false
        try {
            thread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        while (true) {
            tickStart.acquireUninterruptibly();
            if (!alive) {
                return;
            }
            try {
                component.onTick(pendingCycle);
            } catch (RuntimeException e) {
                // Un error en un computador no debe congelar el reloj: se reporta
                // y se libera igual el permiso para que la barrera no se quede esperando.
                System.err.println("[" + thread.getName() + "] error en el ciclo "
                        + pendingCycle + ": " + e);
            } finally {
                tickDone.release();
            }
        }
    }

    public String getThreadName() {
        return thread.getName();
    }
}
