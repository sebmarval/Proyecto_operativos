/*
 */
/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.model;

import com.avilaos.enums.BlockReason;
import com.avilaos.enums.MicroInstruction;
import com.avilaos.enums.ProcessState;
import com.avilaos.enums.ProcessType;
import com.avilaos.sync.BoundedBuffer;

/**
 * Bloque de control de proceso (PCB).
 *
 * Contiene los datos que exige el enunciado (ID global, nombre, computador,
 * estado, tipo, prioridad, memoria, deadline y tiempo restante) más los
 * contadores necesarios para métricas y para el modelo de E/S.
 *
 * El PCB NO decide nada por sí mismo: el kernel llama a sus métodos en el
 * orden fijo del ciclo (ver docs/ARQUITECTURA.md, sección 4).
 *
 * @author aleja
 * @author SebMarval
 */
public class ProcessControlBlock {

    // ------------------------------------------------ datos del enunciado
    private final int id;
    private final String name;
    private final int assignedComputerId;
    private ProcessState state;
    private final ProcessType type;
    private int priority;              // menor número = mayor prioridad
    private final int memoryRequired;
    private int deadline;              // ciclos que le quedan antes de vencer
    private int remainingTime;         // instrucciones que le faltan
    private BlockReason blockReason;
    private final ThreadControlBlock tcb;

    // ------------------------------------------------ modelo de instrucciones
    private final int totalInstructions;

    // ------------------------------------------------ E/S (solo IO_BOUND)
    private int ioEveryCycles;         // pide E/S cada N instrucciones ejecutadas
    private int ioDurationCycles;      // la E/S dura M ciclos globales
    private int cyclesSinceLastIO;

    // ------------------------------------------------ productor / consumidor
    private BoundedBuffer buffer;      // buffer que usa (local o remoto)
    private int itemsToProcess;        // elementos que debe producir/consumir
    private int workCycles;            // ciclos de usuario por elemento
    private int itemsProcessed;

    // ------------------------------------------------ bloqueo temporizado
    private int blockTimer;            // ciclos que faltan para desbloquearse (E/S o latencia)

    // ------------------------------------------------ métricas
    private int arrivalCycle = -1;
    private int firstRunCycle = -1;
    private int finishCycle = -1;
    private int cpuCycles;
    private int readyWaitCycles;
    private int blockedCycles;
    private int semaphoreBlockedCycles;
    private boolean deadlineMissed;

    public ProcessControlBlock(String name, int assignedComputerId,
            ProcessType type, int priority, int memoryRequired, int deadline,
            int totalInstructions) {
        validate(name, assignedComputerId, type, priority, memoryRequired, deadline, totalInstructions);
        this.id = ProcessIdGenerator.nextId();
        this.name = name.trim();
        this.assignedComputerId = assignedComputerId;
        this.state = ProcessState.NEW;
        this.type = type;
        this.priority = priority;
        this.memoryRequired = memoryRequired;
        this.deadline = deadline;
        this.totalInstructions = totalInstructions;
        this.remainingTime = totalInstructions;
        this.blockReason = BlockReason.NONE;
        this.tcb = new ThreadControlBlock("T-" + this.id);
    }

    // ================================================================ productor / consumidor

    /**
     * Crea un productor. Sus instrucciones se precalculan (decisión D5):
     * elementos x (ciclos de trabajo + 5 instrucciones de SO).
     */
    public static ProcessControlBlock createProducer(String name, int computerId, int priority,
            int memoryRequired, int deadline, BoundedBuffer buffer, int items, int workCycles) {
        return createSyncProcess(ProcessType.PRODUCER, name, computerId, priority, memoryRequired,
                deadline, buffer, items, workCycles);
    }

    /** Crea un consumidor (mismo cálculo de instrucciones que el productor). */
    public static ProcessControlBlock createConsumer(String name, int computerId, int priority,
            int memoryRequired, int deadline, BoundedBuffer buffer, int items, int workCycles) {
        return createSyncProcess(ProcessType.CONSUMER, name, computerId, priority, memoryRequired,
                deadline, buffer, items, workCycles);
    }

    private static ProcessControlBlock createSyncProcess(ProcessType type, String name, int computerId,
            int priority, int memoryRequired, int deadline, BoundedBuffer buffer, int items, int workCycles) {
        if (buffer == null) {
            throw new IllegalArgumentException("Un " + type + " necesita un buffer");
        }
        if (items <= 0) {
            throw new IllegalArgumentException("La cantidad de elementos debe ser mayor que 0");
        }
        if (workCycles <= 0) {
            throw new IllegalArgumentException("Los ciclos de trabajo por elemento deben ser mayores que 0");
        }
        int total = items * (workCycles + MicroInstruction.SYSTEM_OPS_PER_ITEM);
        ProcessControlBlock p = new ProcessControlBlock(name, computerId, type, priority,
                memoryRequired, deadline, total);
        p.buffer = buffer;
        p.itemsToProcess = items;
        p.workCycles = workCycles;
        return p;
    }

    public boolean isSyncProcess() {
        return type == ProcessType.PRODUCER || type == ProcessType.CONSUMER;
    }

    /**
     * Instrucción que el proceso ejecutará en su próximo ciclo de CPU.
     * Se deduce del PC: posición dentro del micro-programa de cada elemento.
     */
    public MicroInstruction currentInstruction() {
        if (!isSyncProcess() || isFinished()) {
            return MicroInstruction.USER_WORK;
        }
        return instructionAt(positionInItem(getProgramCounter()));
    }

    /** Posición (0..k+4) de la instrucción número 'pc' dentro de su elemento. */
    public int positionInItem(int pc) {
        return pc % (workCycles + MicroInstruction.SYSTEM_OPS_PER_ITEM);
    }

    private MicroInstruction instructionAt(int pos) {
        if (type == ProcessType.PRODUCER) {
            if (pos < workCycles) {
                return MicroInstruction.USER_WORK;
            }
            return switch (pos - workCycles) {
                case 0 -> MicroInstruction.WAIT_EMPTY;
                case 1 -> MicroInstruction.WAIT_MUTEX;
                case 2 -> MicroInstruction.ADD_ITEM;
                case 3 -> MicroInstruction.SIGNAL_MUTEX;
                default -> MicroInstruction.SIGNAL_FULL;
            };
        }
        return switch (pos) {
            case 0 -> MicroInstruction.WAIT_FULL;
            case 1 -> MicroInstruction.WAIT_MUTEX;
            case 2 -> MicroInstruction.REMOVE_ITEM;
            case 3 -> MicroInstruction.SIGNAL_MUTEX;
            case 4 -> MicroInstruction.SIGNAL_EMPTY;
            default -> MicroInstruction.USER_WORK;
        };
    }

    /** Lo llama el kernel al ejecutar ADD_ITEM / REMOVE_ITEM. */
    public void countItemProcessed() {
        itemsProcessed++;
    }

    public BoundedBuffer getBuffer() { return buffer; }
    public int getItemsToProcess() { return itemsToProcess; }
    public int getItemsProcessed() { return itemsProcessed; }
    public int getWorkCycles() { return workCycles; }

    // ================================================================ E/S

    /** Configura la E/S de un proceso IO_BOUND (decisión D4). */
    public void configureIO(int everyCycles, int durationCycles) {
        if (type != ProcessType.IO_BOUND) {
            throw new IllegalStateException("Solo un proceso IO_BOUND tiene E/S");
        }
        if (everyCycles <= 0 || durationCycles <= 0) {
            throw new IllegalArgumentException("Los ciclos de E/S deben ser mayores que 0");
        }
        this.ioEveryCycles = everyCycles;
        this.ioDurationCycles = durationCycles;
    }

    /** True si, tras la última instrucción, el proceso debe pedir E/S. */
    public boolean needsIO() {
        return type == ProcessType.IO_BOUND
                && ioEveryCycles > 0
                && !isFinished()
                && cyclesSinceLastIO >= ioEveryCycles;
    }

    public int getIoDurationCycles() {
        return ioDurationCycles;
    }

    // ================================================================ ejecución

    /**
     * Ejecuta UNA instrucción (1 ciclo de CPU): descuenta tiempo restante y
     * avanza PC y MAR. Lo llama la CPU únicamente cuando el proceso está en RUNNING.
     */
    public void executeInstruction() {
        if (state != ProcessState.RUNNING) {
            throw new IllegalStateException("P" + id + " no está en ejecución");
        }
        if (remainingTime > 0) {
            remainingTime--;
            tcb.advance();
            cpuCycles++;
            cyclesSinceLastIO++;
        }
    }

    public boolean isFinished() {
        return remainingTime == 0;
    }

    // ================================================================ bloqueo

    /**
     * Bloquea el proceso por un número fijo de ciclos (E/S o latencia de red).
     * Los bloqueos por semáforo NO usan temporizador: duran hasta un semSignal.
     */
    public void blockFor(BlockReason reason, int cycles) {
        setState(ProcessState.BLOCKED);
        this.blockReason = reason;
        this.blockTimer = cycles;
        if (reason == BlockReason.IO_WAIT) {
            cyclesSinceLastIO = 0;
        }
    }

    /** Descuenta un ciclo al temporizador. Devuelve true si ya puede desbloquearse. */
    public boolean tickBlockTimer() {
        if (blockTimer > 0) {
            blockTimer--;
        }
        return blockTimer == 0;
    }

    public boolean hasBlockTimer() {
        return blockReason == BlockReason.IO_WAIT || blockReason == BlockReason.NETWORK_LATENCY;
    }

    public int getBlockTimer() {
        return blockTimer;
    }

    // ================================================================ deadline

    /**
     * Descuenta 1 ciclo del deadline. Se llama en CADA tick global, sin importar
     * el estado (un proceso esperando también se acerca a su límite).
     * Devuelve true si el deadline acaba de vencer.
     */
    public boolean tickDeadline() {
        if (state == ProcessState.TERMINATED || deadline <= 0) {
            return false;
        }
        deadline--;
        if (deadline == 0 && !isFinished()) {
            deadlineMissed = true;
            return true;
        }
        return false;
    }

    public boolean isDeadlineMissed() {
        return deadlineMissed;
    }

    // ================================================================ métricas

    /** Contabiliza un ciclo según el estado actual (lo llama el kernel en cada tick). */
    public void accountCycle() {
        if (state == ProcessState.READY) {
            readyWaitCycles++;
        } else if (state == ProcessState.BLOCKED) {
            blockedCycles++;
            if (isSemaphoreBlock()) {
                semaphoreBlockedCycles++;
            }
        }
    }

    private boolean isSemaphoreBlock() {
        return blockReason == BlockReason.SEMAPHORE_MUTEX
                || blockReason == BlockReason.BUFFER_FULL
                || blockReason == BlockReason.BUFFER_EMPTY;
    }

    public void markArrival(int cycle) {
        if (arrivalCycle < 0) {
            arrivalCycle = cycle;
        }
    }

    public void markFirstRun(int cycle) {
        if (firstRunCycle < 0) {
            firstRunCycle = cycle;
        }
    }

    public void markFinish(int cycle) {
        finishCycle = cycle;
    }

    /** Tiempo de respuesta = primer ciclo en CPU - ciclo de llegada (-1 si nunca ejecutó). */
    public int getResponseTime() {
        return (firstRunCycle < 0 || arrivalCycle < 0) ? -1 : firstRunCycle - arrivalCycle;
    }

    /** Tiempo total en el sistema (retorno) = fin - llegada (-1 si no ha terminado). */
    public int getTurnaroundTime() {
        return (finishCycle < 0 || arrivalCycle < 0) ? -1 : finishCycle - arrivalCycle;
    }

    // ================================================================ estado

    public void setState(ProcessState state) {
        this.state = state;
        this.tcb.setState(state);
        if (state != ProcessState.BLOCKED) {
            this.blockReason = BlockReason.NONE;
            this.blockTimer = 0;
        }
    }

    /** Bloqueo sin temporizador (semáforos). */
    public void block(BlockReason reason) {
        setState(ProcessState.BLOCKED);
        this.blockReason = reason;
    }

    // ================================================================ getters

    public int getId() { return id; }
    public String getName() { return name; }
    public int getAssignedComputerId() { return assignedComputerId; }
    public ProcessState getState() { return state; }
    public ProcessType getType() { return type; }
    public int getPriority() { return priority; }
    public int getMemoryRequired() { return memoryRequired; }
    public int getDeadline() { return deadline; }
    public int getRemainingTime() { return remainingTime; }
    public int getTotalInstructions() { return totalInstructions; }
    public BlockReason getBlockReason() { return blockReason; }
    public ThreadControlBlock getTcb() { return tcb; }
    public int getProgramCounter() { return tcb.getProgramCounter(); }
    public int getArrivalCycle() { return arrivalCycle; }
    public int getFirstRunCycle() { return firstRunCycle; }
    public int getFinishCycle() { return finishCycle; }
    public int getCpuCycles() { return cpuCycles; }
    public int getReadyWaitCycles() { return readyWaitCycles; }
    public int getBlockedCycles() { return blockedCycles; }
    public int getSemaphoreBlockedCycles() { return semaphoreBlockedCycles; }

    public void setPriority(int priority) {
        if (priority < 0) {
            throw new IllegalArgumentException("La prioridad no puede ser negativa");
        }
        this.priority = priority;
    }

    @Override
    public String toString() {
        return "P" + id + " " + name + " [" + state
                + (state == ProcessState.BLOCKED ? ":" + blockReason : "")
                + ", PC=" + getProgramCounter() + ", rest=" + remainingTime
                + ", dl=" + deadline + "]";
    }

    // ================================================================ validación

    private static void validate(String name, int computerId, ProcessType type, int priority,
            int memory, int deadline, int instructions) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre no puede estar vacío");
        }
        if (computerId < 0) {
            throw new IllegalArgumentException("Computador inválido");
        }
        if (type == null) {
            throw new IllegalArgumentException("El tipo es obligatorio");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("La prioridad no puede ser negativa");
        }
        if (memory <= 0) {
            throw new IllegalArgumentException("La memoria debe ser mayor que 0");
        }
        if (deadline <= 0) {
            throw new IllegalArgumentException("El deadline debe ser mayor que 0");
        }
        if (instructions <= 0) {
            throw new IllegalArgumentException("Las instrucciones deben ser mayores que 0");
        }
    }
}
