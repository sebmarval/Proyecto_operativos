/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.sync;

import com.avilaos.enums.SemaphoreType;
import com.avilaos.model.ProcessControlBlock;
import java.util.concurrent.Semaphore;

/**
 * Buffer acotado alojado en un computador, compartido por productores y
 * consumidores locales o remotos.
 *
 * Diseño:
 *  - Arreglo circular de 'capacity' posiciones (in / out / count).
 *  - Tres semáforos SIMULADOS: s (mutex), n (llenos) y e (vacíos).
 *  - Cada método público es UNA operación atómica = UNA instrucción del
 *    proceso (1 ciclo). Nunca se hacen semWait + añadir + semSignal juntos:
 *    el kernel llama a un método por ciclo según el micro-programa del proceso.
 *
 * Concurrencia real: en un mismo tick, varios computadores (hilos de Java)
 * pueden operar sobre el mismo buffer. Por eso cada método se ejecuta dentro
 * de 'lock', un java.util.concurrent.Semaphore binario.
 * Orden de locks del sistema: lock del kernel -> lock del buffer. El buffer
 * nunca llama a un kernel, así que no puede haber interbloqueo.
 *
 * Memoria: el buffer ocupa 'capacity' unidades de RAM en su anfitrión
 * (decisión de diseño: 1 unidad por posición). La reserva la hace quien lo crea.
 *
 * @author aleja
 * @author SebMarval
 */
public class BoundedBuffer {

    private final String id;
    private final int capacity;
    private final int hostComputerId;

    private final int[] slots;
    private int in;
    private int out;
    private int count;
    private int nextItem = 1;
    private int produced;
    private int consumed;

    private final SimSemaphore mutex;   // s
    private final SimSemaphore full;    // n
    private final SimSemaphore empty;   // e

    private final Semaphore lock = new Semaphore(1);

    public BoundedBuffer(String id, int capacity, int hostComputerId) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("El buffer necesita un identificador");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("La capacidad del buffer debe ser mayor que 0");
        }
        if (hostComputerId < 0) {
            throw new IllegalArgumentException("Computador anfitrión inválido");
        }
        this.id = id.trim();
        this.capacity = capacity;
        this.hostComputerId = hostComputerId;
        this.slots = new int[capacity];
        this.mutex = new SimSemaphore(this.id + ".s", SemaphoreType.MUTEX, 1);
        this.full = new SimSemaphore(this.id + ".n", SemaphoreType.FULL, 0);
        this.empty = new SimSemaphore(this.id + ".e", SemaphoreType.EMPTY, capacity);
    }

    // ================================================================ operaciones (1 ciclo c/u)

    /** semWait sobre s, n o e. @return true si el proceso quedó bloqueado. */
    public boolean semWait(SemaphoreType type, ProcessControlBlock process) {
        lock.acquireUninterruptibly();
        try {
            return semaphore(type).semWait(process);
        } finally {
            lock.release();
        }
    }

    /** semSignal sobre s, n o e. @return el proceso a despertar, o null. */
    public ProcessControlBlock semSignal(SemaphoreType type) {
        lock.acquireUninterruptibly();
        try {
            return semaphore(type).semSignal();
        } finally {
            lock.release();
        }
    }

    /** Inserta un elemento nuevo. @return el número del elemento producido. */
    public int add() {
        lock.acquireUninterruptibly();
        try {
            if (count == capacity) {
                // Con los semáforos bien usados esto es imposible: si ocurre, hay un error de sincronización.
                throw new IllegalStateException("Buffer " + id + " lleno: se omitió semWait(e)");
            }
            int item = nextItem++;
            slots[in] = item;
            in = (in + 1) % capacity;
            count++;
            produced++;
            return item;
        } finally {
            lock.release();
        }
    }

    /** Extrae el elemento más antiguo. @return el número del elemento consumido. */
    public int remove() {
        lock.acquireUninterruptibly();
        try {
            if (count == 0) {
                throw new IllegalStateException("Buffer " + id + " vacío: se omitió semWait(n)");
            }
            int item = slots[out];
            slots[out] = 0;
            out = (out + 1) % capacity;
            count--;
            consumed++;
            return item;
        } finally {
            lock.release();
        }
    }

    /**
     * Saca a un proceso de la cola del semáforo donde esté esperando (si murió
     * esperando). @return true si estaba en alguna cola.
     */
    public boolean cancelWait(ProcessControlBlock process) {
        lock.acquireUninterruptibly();
        try {
            return mutex.cancelWait(process) || full.cancelWait(process) || empty.cancelWait(process);
        } finally {
            lock.release();
        }
    }

    // ================================================================ consultas

    /** Copia consistente del estado completo (para la GUI y las métricas). */
    public BufferStatus snapshot() {
        lock.acquireUninterruptibly();
        try {
            int[] items = new int[count];
            for (int i = 0; i < count; i++) {
                items[i] = slots[(out + i) % capacity];
            }
            return new BufferStatus(id, hostComputerId, capacity, count, items,
                    mutex.getValue(), full.getValue(), empty.getValue(),
                    mutex.getWaitingSnapshot(), full.getWaitingSnapshot(), empty.getWaitingSnapshot(),
                    produced, consumed);
        } finally {
            lock.release();
        }
    }

    private SimSemaphore semaphore(SemaphoreType type) {
        return switch (type) {
            case MUTEX -> mutex;
            case FULL -> full;
            case EMPTY -> empty;
        };
    }

    public String getId() { return id; }
    public int getCapacity() { return capacity; }
    public int getHostComputerId() { return hostComputerId; }

    /** Unidades de RAM que ocupa en su computador anfitrión. */
    public int getMemoryRequired() { return capacity; }

    /** True si el proceso de ese computador accede al buffer de forma remota. */
    public boolean isRemoteFor(int computerId) {
        return computerId != hostComputerId;
    }

    @Override
    public String toString() {
        BufferStatus st = snapshot();
        return "Buffer " + id + "@PC" + hostComputerId + " [" + st.count() + "/" + capacity
                + "] s=" + st.valueS() + " n=" + st.valueN() + " e=" + st.valueE();
    }
}
