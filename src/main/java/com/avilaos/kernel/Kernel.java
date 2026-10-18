/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.kernel;

import com.avilaos.datastructures.CustomLinkedList;
import com.avilaos.datastructures.CustomQueue;
import com.avilaos.enums.BlockReason;
import com.avilaos.enums.MicroInstruction;
import com.avilaos.enums.ProcessState;
import com.avilaos.enums.ProcessType;
import com.avilaos.enums.SchedulingPolicy;
import com.avilaos.enums.SemaphoreType;
import com.avilaos.hardware.CPU;
import com.avilaos.hardware.RAM;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.scheduling.SchedulerFactory;
import com.avilaos.scheduling.SchedulingAlgorithm;
import com.avilaos.sync.BoundedBuffer;
import java.util.concurrent.Semaphore;

/**
 * Núcleo de UN computador.
 *
 * Responsabilidades:
 *  - Mantener las colas de estados: nuevos, listos, bloqueados y terminados.
 *  - Planificador de largo plazo: admitir de NEW a READY solo si hay RAM.
 *  - Planificador de corto plazo: elegir, despachar y expropiar según la política.
 *  - Ejecutar las llamadas al sistema de productores/consumidores
 *    (semWait, semSignal, añadir, extraer), una por ciclo.
 *  - Atender el fin de cada instrucción: terminar, pedir E/S, bloquearse, vencer deadline.
 *
 * Concurrencia (Semaphores de Java):
 *  - 'lock' protege las colas del kernel (hilo del computador + hilo de la GUI).
 *  - 'mailboxLock' protege el buzón de despertares, que pueden llenar otros
 *    computadores. Nunca se toma el lock de otro kernel: no hay interbloqueo.
 *  Orden de adquisición: lock del kernel -> lock del buffer -> mailboxLock.
 *
 * El orden de lo que ocurre en un ciclo está fijado en docs/ARQUITECTURA.md (sección 4).
 *
 * @author SebMarval
 * @author aleja
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
    private final CustomLinkedList<BoundedBuffer> hostedBuffers = new CustomLinkedList<>();

    private final CustomQueue<ProcessControlBlock> wakeUpMailbox = new CustomQueue<>();
    private final Semaphore mailboxLock = new Semaphore(1);
    private WakeUpRouter wakeUpRouter;

    private SchedulingAlgorithm scheduler;
    private SchedulingPolicy policy;
    private int quantum;

    private int completedCount;
    private int deadlineMissedCount;
    private int currentCycle;

    public Kernel(int computerId, int ramCapacity, SchedulingPolicy initialPolicy,
            int initialQuantum, EventLogger logger) {
        this.computerId = computerId;
        this.ram = new RAM(ramCapacity);
        this.cpu = new CPU(computerId);
        this.logger = (logger == null) ? EventLogger.SILENT : logger;
        this.scheduler = SchedulerFactory.create(initialPolicy, initialQuantum);
        this.policy = initialPolicy;
        this.quantum = initialQuantum;
        this.wakeUpRouter = this::deliverWakeUp;   // por defecto: solo procesos propios
    }

    // ================================================================ creación

    /**
     * Recibe un proceso nuevo (desde la GUI o la carga inicial) y lo deja en la
     * cola de nuevos. No reserva RAM: eso ocurre en la admisión.
     *
     * @throws IllegalArgumentException si el proceso es de otro computador,
     *         pide más memoria que la RAM total o es productor/consumidor sin buffer
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
        if (process.isSyncProcess() && process.getBuffer() == null) {
            throw new IllegalArgumentException("P" + process.getId() + " es " + process.getType()
                    + " y no tiene buffer asignado");
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

    /**
     * Aloja un buffer en este computador: reserva su memoria en la RAM.
     *
     * @throws IllegalArgumentException si el buffer es de otro computador o no hay RAM
     */
    public void hostBuffer(BoundedBuffer buffer) {
        if (buffer.getHostComputerId() != computerId) {
            throw new IllegalArgumentException("El buffer " + buffer.getId()
                    + " pertenece al computador " + buffer.getHostComputerId());
        }
        lock.acquireUninterruptibly();
        try {
            if (!ram.allocate(buffer.getMemoryRequired())) {
                throw new IllegalArgumentException("RAM insuficiente para el buffer " + buffer.getId()
                        + " (pide " + buffer.getMemoryRequired() + ", libre " + ram.getFree() + ")");
            }
            hostedBuffers.add(buffer);
            logger.log(currentCycle, computerId, "Buffer " + buffer.getId() + " creado (capacidad "
                    + buffer.getCapacity() + ", " + ram.getUsed() + "/" + ram.getCapacity() + " RAM)");
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
            processWakeUps();           // 0. despertares por semSignal (del ciclo anterior)
            updateBlockedTimers();      // 1. E/S y latencia
            admitNewProcesses();        // 2. largo plazo
            scheduleIfNeeded();         // 3. corto plazo
            executeInstruction();       // 4. una instrucción (usuario o SO)
            handleAfterExecution();     // 5. termina / pide E/S / se bloquea
            updateDeadlines();          // 6. deadlines en todos los estados
            accountCycle();             // 7. métricas del ciclo
        } finally {
            lock.release();
        }
    }

    // 0 -----------------------------------------------------------------
    /**
     * Mueve a READY los procesos despertados por un semSignal. Si el proceso ya
     * no está bloqueado (p. ej. venció su deadline), el aviso se descarta.
     */
    private void processWakeUps() {
        mailboxLock.acquireUninterruptibly();
        try {
            ProcessControlBlock p = wakeUpMailbox.dequeue();
            while (p != null) {
                if (blockedList.remove(p)) {
                    BlockReason reason = p.getBlockReason();
                    p.setState(ProcessState.READY);
                    readyQueue.add(p);
                    logger.log(currentCycle, computerId, "P" + p.getId() + " despertado (" + reason + ") -> READY");
                }
                p = wakeUpMailbox.dequeue();
            }
        } finally {
            mailboxLock.release();
        }
    }

    /** Buzón de despertares: lo llaman otros computadores (o este mismo). */
    public void deliverWakeUp(ProcessControlBlock process) {
        if (process.getAssignedComputerId() != computerId) {
            throw new IllegalArgumentException("P" + process.getId() + " no pertenece al computador " + computerId);
        }
        mailboxLock.acquireUninterruptibly();
        try {
            wakeUpMailbox.enqueue(process);
        } finally {
            mailboxLock.release();
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

        // Expropiación: la política activa decide si el proceso en CPU debe salir.
        if (running != null && scheduler.shouldPreempt(running, readyQueue, cpu.getQuantumUsed())) {
            cpu.release();
            running.setState(ProcessState.READY);
            readyQueue.add(running);
            logger.log(currentCycle, computerId, "P" + running.getId() + " expropiado (" + policy + ") -> READY");
        }

        // Despacho: si la CPU está libre, la política elige al siguiente.
        if (cpu.isIdle() && !readyQueue.isEmpty()) {
            int index = scheduler.selectNextIndex(readyQueue);
            if (index != -1) {
                ProcessControlBlock next = readyQueue.removeAt(index);
                cpu.dispatch(next, currentCycle);
                logger.log(currentCycle, computerId, "P" + next.getId() + " despachado -> RUNNING");
            }
        }
    }

    // 4 -----------------------------------------------------------------
    private void executeInstruction() {
        ProcessControlBlock running = cpu.getRunning();
        if (running == null) {
            cpu.executeCycle();                 // ciclo ocioso
            return;
        }
        MicroInstruction instruction = running.currentInstruction();
        if (!instruction.isSystemCall()) {
            cpu.executeCycle();                 // instrucción de usuario
            return;
        }
        cpu.executeKernelInstruction();         // llamada al sistema: modo SO
        executeSystemCall(running, instruction);
    }

    /** Semántica de una llamada al sistema sobre el buffer del proceso. */
    private void executeSystemCall(ProcessControlBlock p, MicroInstruction instruction) {
        BoundedBuffer buffer = p.getBuffer();
        String who = "P" + p.getId();
        if (instruction.isWait()) {
            SemaphoreType sem = instruction.getSemaphore();
            if (buffer.semWait(sem, p)) {
                p.block(sem.getBlockReason());  // sale de la CPU en el paso 5
                logger.log(currentCycle, computerId, who + " semWait(" + buffer.getId() + "." + sem.getSymbol()
                        + ") -> BLOCKED " + sem.getBlockReason());
            }
        } else if (instruction.isSignal()) {
            signalAndRoute(buffer, instruction.getSemaphore());
        } else if (instruction == MicroInstruction.ADD_ITEM) {
            int item = buffer.add();
            p.countItemProcessed();
            logger.log(currentCycle, computerId, who + " produce elemento #" + item + " en " + buffer.getId());
        } else if (instruction == MicroInstruction.REMOVE_ITEM) {
            int item = buffer.remove();
            p.countItemProcessed();
            logger.log(currentCycle, computerId, who + " consume elemento #" + item + " de " + buffer.getId());
        }
    }

    /** semSignal + entrega del proceso despertado al kernel de su computador. */
    private void signalAndRoute(BoundedBuffer buffer, SemaphoreType sem) {
        ProcessControlBlock woken = buffer.semSignal(sem);
        if (woken != null) {
            wakeUpRouter.deliverWakeUp(woken);
        }
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
        } else if (running.getState() == ProcessState.BLOCKED) {
            cpu.release();                       // se bloqueó en un semáforo
            blockedList.add(running);
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
        releaseSyncResources(p);
        terminate(p, holdsMemory, "deadline vencido");
    }

    /**
     * Un productor/consumidor que muere a mitad de su micro-programa podría
     * dejar el buffer trabado (por ejemplo, con el mutex s tomado). Aquí se
     * devuelve todo lo que tenía, según la posición en la que murió.
     *
     *  - Si murió esperando en un semáforo, se cancela esa espera.
     *  - Si tenía el mutex s, se libera.
     *  - Productor: si reservó espacio (e) y no añadió, lo devuelve;
     *    si añadió y no avisó (n), avisa.
     *  - Consumidor: si tomó un elemento (n) y no extrajo, lo devuelve;
     *    si extrajo y no avisó (e), avisa.
     */
    private void releaseSyncResources(ProcessControlBlock p) {
        if (!p.isSyncProcess() || p.getState() == ProcessState.NEW) {
            return;
        }
        BoundedBuffer buffer = p.getBuffer();
        int pos = p.positionInItem(p.getProgramCounter());   // próxima instrucción
        if (buffer.cancelWait(p)) {
            pos--;                                            // ese semWait queda deshecho
        }
        int base = (p.getType() == ProcessType.PRODUCER) ? p.getWorkCycles() : 0;
        int step = pos - base;   // instrucciones de SO ya completadas en este elemento (0..5)

        boolean holdsMutex = (step == 2 || step == 3);
        if (holdsMutex) {
            signalAndRoute(buffer, SemaphoreType.MUTEX);
        }
        if (p.getType() == ProcessType.PRODUCER) {
            if (step == 1 || step == 2) {
                signalAndRoute(buffer, SemaphoreType.EMPTY);   // devuelve el espacio reservado
            } else if (step == 3 || step == 4) {
                signalAndRoute(buffer, SemaphoreType.FULL);    // el elemento ya está: avisa
            }
        } else {
            if (step == 1 || step == 2) {
                signalAndRoute(buffer, SemaphoreType.FULL);    // devuelve el elemento reservado
            } else if (step == 3 || step == 4) {
                signalAndRoute(buffer, SemaphoreType.EMPTY);   // ya extrajo: avisa espacio libre
            }
        }
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

    // ================================================================ política

    /** Cambia la política de planificación en plena ejecución (independiente por computador). */
    public void setSchedulingPolicy(SchedulingPolicy newPolicy, int newQuantum) {
        SchedulingAlgorithm created = SchedulerFactory.create(newPolicy, newQuantum); // valida antes de tomar el lock
        lock.acquireUninterruptibly();
        try {
            this.scheduler = created;
            this.policy = newPolicy;
            this.quantum = newQuantum;
            logger.log(currentCycle, computerId, "Política cambiada a " + newPolicy
                    + (newPolicy == SchedulingPolicy.ROUND_ROBIN ? " (quantum " + newQuantum + ")" : ""));
        } finally {
            lock.release();
        }
    }

    /** Lo usa el Cluster para que los semSignal remotos lleguen al kernel correcto. */
    public void setWakeUpRouter(WakeUpRouter router) {
        if (router == null) {
            throw new IllegalArgumentException("El router no puede ser null");
        }
        this.wakeUpRouter = router;
    }

    // ================================================================ consultas (copias para la GUI)

    public Object[] getNewSnapshot()        { return snapshot(newQueue); }
    public Object[] getReadySnapshot()      { return snapshot(readyQueue); }
    public Object[] getBlockedSnapshot()    { return snapshot(blockedList); }
    public Object[] getTerminatedSnapshot() { return snapshot(terminatedList); }

    public Object[] getHostedBuffersSnapshot() {
        lock.acquireUninterruptibly();
        try {
            return hostedBuffers.toArray();
        } finally {
            lock.release();
        }
    }

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

    public int getComputerId()                   { return computerId; }
    public RAM getRam()                          { return ram; }
    public CPU getCpu()                          { return cpu; }
    public int getCompletedCount()               { return completedCount; }
    public int getDeadlineMissedCount()          { return deadlineMissedCount; }
    public int getCurrentCycle()                 { return currentCycle; }
    public SchedulingPolicy getSchedulingPolicy(){ return policy; }
    public int getQuantum()                      { return quantum; }
}
