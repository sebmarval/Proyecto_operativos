/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.kernel;

import com.avilaos.datastructures.CustomLinkedList;
import com.avilaos.datastructures.CustomQueue;
import com.avilaos.enums.BlockReason;
import com.avilaos.enums.ProcessState;
import com.avilaos.enums.SchedulingPolicy;
import com.avilaos.hardware.CPU;
import com.avilaos.hardware.RAM;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.scheduling.SchedulerFactory;
import com.avilaos.scheduling.SchedulingAlgorithm;
import java.util.concurrent.Semaphore;

/**
 * Núcleo de UN computador.
 *
 * Responsabilidades:
 *  - Mantener las colas de estados: nuevos, listos, bloqueados y terminados.
 *  - Planificador de largo plazo: admitir de NEW a READY solo si hay RAM.
 *  - Planificador de corto plazo: elegir y despachar al siguiente proceso.
 *  - Atender el fin de cada instrucción: terminar, pedir E/S, vencer deadline.
 *
 * Concurrencia: las colas las tocan el hilo del computador (cada ciclo) y el
 * hilo de la GUI (crear procesos, leer el estado). Todo acceso pasa por
 * 'lock', un Semaphore de Java binario. Las estructuras propias no tienen
 * sincronización interna, por eso este lock es obligatorio.
 *
 * El orden de lo que ocurre en un ciclo está fijado en docs/ARQUITECTURA.md (sección 4).
 *
 * @author SebMarval
 */
public class Kernel {

    private final int computerId;
    private final RAM ram;
    private final CPU cpu;
    private final EventLogger logger;
    private final Semaphore lock = new Semaphore(1);

    private final CustomQueue<ProcessControlBlock> newQueue = new CustomQueue<>();
    private final CustomLinkedList<ProcessControlBlock> readyQueue = new CustomLinkedList<>();
    private final CustomLinkedList<ProcessControlBlock> blockedList = new CustomLinkedList<>();
    private final CustomLinkedList<ProcessControlBlock> terminatedList = new CustomLinkedList<>();

    private int completedCount;
    private int deadlineMissedCount;
    private int currentCycle;
    private SchedulingAlgorithm scheduler;

    public Kernel(int computerId, int ramCapacity, SchedulingPolicy initialPolicy, int initialQuantum, EventLogger logger) {
    this.computerId = computerId;
    this.ram = new RAM(ramCapacity);
    this.cpu = new CPU(computerId);
    this.logger = (logger == null) ? EventLogger.SILENT : logger;
    this.scheduler = SchedulerFactory.create(initialPolicy, initialQuantum);
}

    // ================================================================ creación

    /**
     * Recibe un proceso nuevo (desde la GUI o la carga inicial) y lo deja en la
     * cola de nuevos. No reserva RAM: eso ocurre en la admisión.
     *
     * @throws IllegalArgumentException si el proceso es de otro computador o
     *         pide más memoria que la RAM total (nunca podría ser admitido)
     */
    public void submit(ProcessControlBlock process) {
        if (process.getAssignedComputerId() != computerId) {
            throw new IllegalArgumentException("P" + process.getId()
                    + " pertenece al computador " + process.getAssignedComputerId());
        }
        if (process.getMemoryRequired() > ram.getCapacity()) {
            throw new IllegalArgumentException("P" + process.getId() + " pide "
                    + process.getMemoryRequired() + " de memoria y la RAM total es " + ram.getCapacity());
        }
        lock.acquireUninterruptibly();
        try {
            process.markArrival(currentCycle);
            newQueue.enqueue(process);
            logger.log(currentCycle, computerId, "P" + process.getId() + " (" + process.getName() + ") creado -> NEW");
        } finally {
            lock.release();
        }
    }

    // ================================================================ ciclo

    /** Ejecuta UN ciclo completo del computador, en el orden fijado por el contrato. */
    public void runCycle(int cycle) {
        lock.acquireUninterruptibly();
        try {
            currentCycle = cycle;
            updateBlockedTimers();      // 1. E/S y latencia
            admitNewProcesses();        // 2. largo plazo
            scheduleIfNeeded();         // 3. corto plazo
            cpu.executeCycle();         // 4. una instrucción
            handleAfterExecution();     // 5. termina / pide E/S
            updateDeadlines();          // 6. deadlines en todos los estados
            accountCycle();             // 7. métricas del ciclo
        } finally {
            lock.release();
        }
    }

    // 1 -----------------------------------------------------------------
    private void updateBlockedTimers() {
        int i = 0;
        while (i < blockedList.getSize()) {
            ProcessControlBlock p = blockedList.get(i);
            if (p.hasBlockTimer() && p.tickBlockTimer()) {
                BlockReason reason = p.getBlockReason();
                blockedList.removeAt(i);          // no se avanza i: el siguiente ocupó esta posición
                p.setState(ProcessState.READY);
                readyQueue.add(p);
                logger.log(currentCycle, computerId, "P" + p.getId() + " termina " + reason + " -> READY");
            } else {
                i++;
            }
        }
    }

    // 2 -----------------------------------------------------------------
    /**
     * Admisión FIFO estricta: si el primero de la cola no cabe, nadie lo
     * adelanta (evita que un proceso grande sufra inanición).
     */
    private void admitNewProcesses() {
        ProcessControlBlock next = newQueue.peek();
        while (next != null && ram.allocate(next.getMemoryRequired())) {
            newQueue.dequeue();
            next.setState(ProcessState.READY);
            readyQueue.add(next);
            logger.log(currentCycle, computerId, "P" + next.getId() + " admitido -> READY ("
                    + ram.getUsed() + "/" + ram.getCapacity() + " RAM)");
            next = newQueue.peek();
        }
    }
    
 

    // 3 -----------------------------------------------------------------
    private void scheduleIfNeeded() {
    ProcessControlBlock running = cpu.getRunning();

    // 1. Revisa si la politica activa exige expropiar al proceso en ejecucion
    if (running != null && scheduler != null && scheduler.shouldPreempt(running, readyQueue, cpu.getQuantumUsed())) {
        cpu.release();
        running.setState(ProcessState.READY);
        readyQueue.add(running);
        logger.log(currentCycle, computerId, "P" + running.getId() + " expropiado -> READY");
    }

    // 2. Si la CPU esta libre, selecciona y despacha al siguiente proceso elegible
    if (cpu.isIdle() && !readyQueue.isEmpty()) {
        int index = selectNextIndex();
        if (index != -1) {
            ProcessControlBlock next = readyQueue.removeAt(index);
            cpu.dispatch(next, currentCycle);
            logger.log(currentCycle, computerId, "P" + next.getId() + " despachado -> RUNNING");
        }
    }
}

    /**
     * Índice del próximo proceso en la cola de listos.
     * Provisional: FCFS (el primero). En el Paso 6 se delega en SchedulingAlgorithm.
     */
    private int selectNextIndex() {
    if (scheduler == null) {
        return 0; // FCFS por defecto
    }
    // Delega la eleccion a la politica activa (FCFS, RR, EDF o Prioridades)
    return scheduler.selectNextIndex(readyQueue); 
}

    // 5 -----------------------------------------------------------------
    private void handleAfterExecution() {
        ProcessControlBlock running = cpu.getRunning();
        if (running == null) {
            return;
        }
        if (running.isFinished()) {
            cpu.release();
            terminate(running, true, "terminó sus instrucciones");
        } else if (running.needsIO()) {
            cpu.release();
            running.blockFor(BlockReason.IO_WAIT, running.getIoDurationCycles());
            blockedList.add(running);
            logger.log(currentCycle, computerId, "P" + running.getId() + " solicita E/S por "
                    + running.getIoDurationCycles() + " ciclos -> BLOCKED");
        }
    }

    // 6 -----------------------------------------------------------------
    private void updateDeadlines() {
        ProcessControlBlock running = cpu.getRunning();
        if (running != null && running.tickDeadline()) {
            cpu.release();
            expire(running, true);
        }
        expireFromList(readyQueue, true);
        expireFromList(blockedList, true);
        expireFromNewQueue();
    }

    private void expireFromList(CustomLinkedList<ProcessControlBlock> list, boolean holdsMemory) {
        int i = 0;
        while (i < list.getSize()) {
            ProcessControlBlock p = list.get(i);
            if (p.tickDeadline()) {
                list.removeAt(i);
                expire(p, holdsMemory);
            } else {
                i++;
            }
        }
    }

    /** Los procesos en NEW también envejecen, pero no tienen RAM que liberar. */
    private void expireFromNewQueue() {
        int pending = newQueue.size();
        for (int k = 0; k < pending; k++) {
            ProcessControlBlock p = newQueue.dequeue();
            if (p.tickDeadline()) {
                expire(p, false);
            } else {
                newQueue.enqueue(p);   // rota la cola conservando el orden FIFO
            }
        }
    }

    private void expire(ProcessControlBlock p, boolean holdsMemory) {
        deadlineMissedCount++;
        terminate(p, holdsMemory, "deadline vencido");
    }

    // 7 -----------------------------------------------------------------
    private void accountCycle() {
        for (int i = 0; i < readyQueue.getSize(); i++) {
            readyQueue.get(i).accountCycle();
        }
        for (int i = 0; i < blockedList.getSize(); i++) {
            blockedList.get(i).accountCycle();
        }
    }

    // ================================================================ terminación

    private void terminate(ProcessControlBlock p, boolean holdsMemory, String reason) {
        p.setState(ProcessState.TERMINATED);
        p.markFinish(currentCycle);
        if (holdsMemory) {
            ram.free(p.getMemoryRequired());
        }
        if (!p.isDeadlineMissed()) {
            completedCount++;
        }
        terminatedList.add(p);
        logger.log(currentCycle, computerId, "P" + p.getId() + " " + reason + " -> TERMINATED");
    }

    // ================================================================ consultas (copias para la GUI)

    public Object[] getNewSnapshot()        { return snapshot(newQueue); }
    public Object[] getReadySnapshot()      { return snapshot(readyQueue); }
    public Object[] getBlockedSnapshot()    { return snapshot(blockedList); }
    public Object[] getTerminatedSnapshot() { return snapshot(terminatedList); }

    private Object[] snapshot(CustomQueue<ProcessControlBlock> queue) {
        lock.acquireUninterruptibly();
        try {
            return queue.toArray();
        } finally {
            lock.release();
        }
    }

    private Object[] snapshot(CustomLinkedList<ProcessControlBlock> list) {
        lock.acquireUninterruptibly();
        try {
            return list.toArray();
        } finally {
            lock.release();
        }
    }

    public int getComputerId()         { return computerId; }
    public RAM getRam()                { return ram; }
    public CPU getCpu()                { return cpu; }
    public int getCompletedCount()     { return completedCount; }
    public int getDeadlineMissedCount(){ return deadlineMissedCount; }
    public int getCurrentCycle()       { return currentCycle; }
    
    
    public void setSchedulingPolicy(SchedulingPolicy policy, int quantum) {
    lock.acquireUninterruptibly();
    try {
        this.scheduler = SchedulerFactory.create(policy, quantum);
    } finally {
        lock.release();
    }
    }
}