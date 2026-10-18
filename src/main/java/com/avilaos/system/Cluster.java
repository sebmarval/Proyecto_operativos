/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.system;

import com.avilaos.datastructures.CustomLinkedList;
import com.avilaos.enums.SchedulingPolicy;
import com.avilaos.hardware.ClockListener;
import com.avilaos.hardware.GlobalClock;
import com.avilaos.kernel.EventLogger;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.sync.BoundedBuffer;
import java.util.concurrent.Semaphore;

/**
 * El sistema distribuido completo: los computadores, el reloj global, la red
 * (latencia) y el registro de buffers compartidos.
 *
 * Es el único punto de entrada que necesitan la GUI y la configuración:
 *  - crear el clúster (n computadores, RAM, política, quantum, ciclo, latencia),
 *  - crear buffers y procesos (en un computador elegido o automático),
 *  - controlar el reloj (iniciar, pausar, reanudar, detener, velocidad).
 *
 * Red: cuando un semSignal despierta a un proceso de otro computador, el
 * clúster lo entrega al buzón del kernel correcto (WakeUpRouter).
 * No hay migración: un proceso vive siempre en el computador donde se creó.
 *
 * @author SebMarval
 */
public final class Cluster {

    public static final int MIN_COMPUTERS = 2;

    private final Computer[] computers;
    private final GlobalClock clock;
    private final EventLogger logger;
    private final CustomLinkedList<BoundedBuffer> buffers = new CustomLinkedList<>();
    private final Semaphore buffersLock = new Semaphore(1);   // la GUI puede crear buffers en ejecución
    private int networkLatency;

    /**
     * @param ramCapacities   RAM de cada computador (su longitud = cantidad de computadores)
     * @param policy          política inicial de todos los computadores
     * @param quantum         quantum inicial (Round Robin)
     * @param cycleDurationMs duración del ciclo del reloj en milisegundos
     * @param networkLatency  ciclos que bloquea cada acceso a un buffer remoto
     */
    public Cluster(int[] ramCapacities, SchedulingPolicy policy, int quantum,
            int cycleDurationMs, int networkLatency, EventLogger logger) {
        if (ramCapacities == null || ramCapacities.length < MIN_COMPUTERS) {
            throw new IllegalArgumentException("El clúster necesita al menos " + MIN_COMPUTERS + " computadores");
        }
        this.logger = (logger == null) ? EventLogger.SILENT : logger;
        this.computers = new Computer[ramCapacities.length];
        for (int i = 0; i < ramCapacities.length; i++) {
            computers[i] = new Computer(i, ramCapacities[i], policy, quantum, this.logger);
            computers[i].getKernel().setWakeUpRouter(this::routeWakeUp);
        }
        setNetworkLatency(networkLatency);
        this.clock = new GlobalClock(computers, cycleDurationMs);
    }

    // ================================================================ red

    /** Entrega un proceso despertado al kernel de SU computador. */
    private void routeWakeUp(ProcessControlBlock process, int cycle) {
        getComputer(process.getAssignedComputerId()).getKernel().deliverWakeUp(process, cycle);
    }

    public final void setNetworkLatency(int cycles) {
        if (cycles < 0) {
            throw new IllegalArgumentException("La latencia de red no puede ser negativa");
        }
        this.networkLatency = cycles;
        for (Computer c : computers) {
            c.getKernel().setNetworkLatency(cycles);
        }
    }

    public int getNetworkLatency() {
        return networkLatency;
    }

    // ================================================================ buffers

    /**
     * Crea un buffer alojado en 'hostComputerId'. Ocupa RAM en ese computador.
     *
     * @throws IllegalArgumentException si el id se repite, el computador no
     *         existe o no hay RAM suficiente
     */
    public BoundedBuffer createBuffer(String id, int capacity, int hostComputerId) {
        Computer host = getComputer(hostComputerId);
        BoundedBuffer buffer = new BoundedBuffer(id, capacity, hostComputerId);   // valida id y capacidad
        buffersLock.acquireUninterruptibly();
        try {
            if (findBuffer(buffer.getId()) != null) {
                throw new IllegalArgumentException("Ya existe un buffer con id " + buffer.getId());
            }
            host.getKernel().hostBuffer(buffer);          // reserva la RAM (o lanza error)
            buffers.add(buffer);
            return buffer;
        } finally {
            buffersLock.release();
        }
    }

    /** @return el buffer con ese id, o null si no existe */
    public BoundedBuffer getBuffer(String id) {
        buffersLock.acquireUninterruptibly();
        try {
            return findBuffer(id);
        } finally {
            buffersLock.release();
        }
    }

    private BoundedBuffer findBuffer(String id) {
        for (int i = 0; i < buffers.getSize(); i++) {
            if (buffers.get(i).getId().equals(id)) {
                return buffers.get(i);
            }
        }
        return null;
    }

    public Object[] getBuffersSnapshot() {
        buffersLock.acquireUninterruptibly();
        try {
            return buffers.toArray();
        } finally {
            buffersLock.release();
        }
    }

    // ================================================================ procesos

    /**
     * Asignación automática (decisión de diseño): el computador con menos
     * procesos vivos; en empate, el de menor id.
     */
    public int pickComputerAutomatically() {
        int best = 0;
        int bestLoad = computers[0].getKernel().getActiveProcessCount();
        for (int i = 1; i < computers.length; i++) {
            int load = computers[i].getKernel().getActiveProcessCount();
            if (load < bestLoad) {
                best = i;
                bestLoad = load;
            }
        }
        return best;
    }

    /** Envía el proceso al computador indicado en su PCB. */
    public void submit(ProcessControlBlock process) {
        getComputer(process.getAssignedComputerId()).submit(process);
    }

    // ================================================================ reloj

    public void start()                      { clock.start(); }
    public void pause()                      { clock.pause(); }
    public void resume()                     { clock.resume(); }
    public void stop()                       { clock.stop(); }
    public void setCycleDurationMs(int ms)   { clock.setCycleDurationMs(ms); }
    public int getCurrentCycle()             { return clock.getCurrentCycle(); }

    /** Aviso al terminar cada ciclo completo (para la GUI y las métricas). */
    public void setAfterTickObserver(ClockListener observer) {
        clock.setAfterTickObserver(observer);
    }

    // ================================================================ consultas

    public Computer getComputer(int id) {
        if (id < 0 || id >= computers.length) {
            throw new IllegalArgumentException("No existe el computador " + id
                    + " (hay " + computers.length + ")");
        }
        return computers[id];
    }

    public int getComputerCount() {
        return computers.length;
    }

    public GlobalClock getClock() {
        return clock;
    }

    public EventLogger getLogger() {
        return logger;
    }
}

