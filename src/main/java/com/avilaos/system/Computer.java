/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.system;

import com.avilaos.enums.SchedulingPolicy;
import com.avilaos.hardware.ClockListener;
import com.avilaos.kernel.EventLogger;
import com.avilaos.kernel.Kernel;
import com.avilaos.model.ProcessControlBlock;

/**
 * Un computador del clúster. Todos los computadores son instancias de esta
 * MISMA clase (requisito del enunciado: no se duplica código por máquina).
 *
 * Cada computador tiene su propio kernel, y el kernel tiene su CPU, su RAM,
 * su planificador y sus colas. El computador reacciona al reloj global
 * (ClockListener): en cada tick ejecuta exactamente un ciclo.
 *
 * En el Paso 5 esta clase pasa a ejecutarse en su propio hilo de Java.
 *
 * @author SebMarval
 */
public class Computer implements ClockListener {

    private final int id;
    private final Kernel kernel;

    public Computer(int id, int ramCapacity, SchedulingPolicy initialPolicy, int initialQuantum, EventLogger logger) {
        if (id < 0) {
            throw new IllegalArgumentException("El id del computador no puede ser negativo");
        }
        this.id = id;
        this.kernel = new Kernel(id, ramCapacity, initialPolicy, initialQuantum, logger);
    }

    @Override
    public void onTick(int cycle) {
        kernel.runCycle(cycle);
    }

    /** Entrada de procesos nuevos a este computador. */
    public void submit(ProcessControlBlock process) {
        kernel.submit(process);
    }

    public int getId() {
        return id;
    }

    public Kernel getKernel() {
        return kernel;
    }

    @Override
    public String toString() {
        return "Computador " + id;
    }
}
