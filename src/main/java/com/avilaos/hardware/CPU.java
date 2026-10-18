/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.hardware;

import com.avilaos.enums.CPUMode;
import com.avilaos.enums.ProcessState;
import com.avilaos.model.ProcessControlBlock;

/**
 * CPU de un computador (un solo núcleo).
 *
 * Responsabilidades:
 *  - Mantener el proceso en ejecución (como máximo uno).
 *  - Ejecutar UNA instrucción por ciclo (supuesto del enunciado).
 *  - Llevar el modo de ejecución (usuario / SO) para la GUI.
 *  - Contar ciclos ocupados y ociosos (utilización de CPU) y el quantum usado.
 *
 * La CPU NO decide a quién ejecutar: eso lo hace el planificador del kernel,
 * que llama a dispatch() y preempt().
 *
 * Decisión de diseño: el cambio de contexto no consume ciclos (el enunciado no
 * lo pide); despachar y ejecutar ocurren en el mismo ciclo.
 *
 * @author SebMarval
 */
public class CPU {

    private final int computerId;
    private ProcessControlBlock running;
    private CPUMode mode;
    private int quantumUsed;
    private int busyCycles;
    private int idleCycles;
    private boolean busyLastCycle;

    public CPU(int computerId) {
        this.computerId = computerId;
        this.running = null;
        this.mode = CPUMode.KERNEL_MODE;
        this.quantumUsed = 0;
    }

    /** Carga un proceso READY en la CPU (despacho). */
    public void dispatch(ProcessControlBlock process, int cycle) {
        if (running != null) {
            throw new IllegalStateException("CPU " + computerId + " ocupada por P" + running.getId());
        }
        if (process.getState() != ProcessState.READY) {
            throw new IllegalStateException("Solo se despacha un proceso READY: " + process);
        }
        process.setState(ProcessState.RUNNING);
        process.markFirstRun(cycle);
        running = process;
        quantumUsed = 0;
    }

    /**
     * Saca al proceso actual de la CPU y lo devuelve (el kernel decide a qué
     * cola va: READY si fue expropiado, BLOCKED o TERMINATED).
     */
    public ProcessControlBlock release() {
        ProcessControlBlock previous = running;
        running = null;
        quantumUsed = 0;
        return previous;
    }

    /**
     * Ejecuta un ciclo: si hay proceso, ejecuta 1 instrucción de usuario;
     * si no, el ciclo se cuenta como ocioso (el SO ejecuta su ciclo ocioso).
     */
    public void executeCycle() {
        if (running == null) {
            mode = CPUMode.KERNEL_MODE;
            idleCycles++;
            busyLastCycle = false;
            return;
        }
        mode = CPUMode.USER_MODE;
        running.executeInstruction();
        busyCycles++;
        quantumUsed++;
        busyLastCycle = true;
    }

    /**
     * Ejecuta un ciclo en modo SO: una llamada al sistema del proceso actual
     * (semWait, semSignal, añadir o extraer). Cuenta como 1 instrucción del
     * proceso: avanza PC/MAR, ocupa la CPU y consume quantum. La semántica de
     * la operación (bloquear, despertar...) la aplica el kernel.
     */
    public void executeKernelInstruction() {
        if (running == null) {
            throw new IllegalStateException("No hay proceso para ejecutar una llamada al sistema");
        }
        mode = CPUMode.KERNEL_MODE;
        running.executeInstruction();
        busyCycles++;
        quantumUsed++;
        busyLastCycle = true;
    }

    // ---------------------------------------------------------------- consultas

    public boolean isIdle() {
        return running == null;
    }

    public ProcessControlBlock getRunning() {
        return running;
    }

    public CPUMode getMode() {
        return mode;
    }

    public int getQuantumUsed() {
        return quantumUsed;
    }

    public int getBusyCycles() {
        return busyCycles;
    }

    public int getIdleCycles() {
        return idleCycles;
    }

    /** True si la CPU trabajó en el último ciclo (para la gráfica de utilización). */
    public boolean wasBusyLastCycle() {
        return busyLastCycle;
    }

    /** Utilización acumulada en porcentaje: ciclos ocupados / ciclos totales. */
    public double getUtilizationPercent() {
        int total = busyCycles + idleCycles;
        return total == 0 ? 0.0 : (busyCycles * 100.0) / total;
    }

    public int getComputerId() {
        return computerId;
    }
}
