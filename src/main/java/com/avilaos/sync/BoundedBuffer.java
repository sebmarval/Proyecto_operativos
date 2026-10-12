/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.sync;
import com.avilaos.model.ProcessControlBlock;

/**
 *
 * @author aleja
 */
public class BoundedBuffer {

    private final String id;
    private final int capacity;
    private final int hostComputerId;
    private final Object[] buffer;
    private int in;
    private int out;
    private int count;

    private final Semaphore mutex;
    private final Semaphore empty;
    private final Semaphore full;

    public BoundedBuffer(String id, int capacity, int hostComputerId) {
        this.id = id;
        this.capacity = capacity;
        this.hostComputerId = hostComputerId;
        this.buffer = new Object[capacity];
        this.in = 0;
        this.out = 0;
        this.count = 0;

        this.mutex = new Semaphore(id + "_mutex", 1);
        this.empty = new Semaphore(id + "_empty", capacity);
        this.full = new Semaphore(id + "_full", 0);
    }

    public void produce(ProcessControlBlock producer, Object item) {
        empty.semWait(producer);
        mutex.semWait(producer);

        buffer[in] = item;
        in = (in + 1) % capacity;
        count++;

        mutex.semSignal();
        full.semSignal();
    }

    public Object consume(ProcessControlBlock consumer) {
        full.semWait(consumer);
        mutex.semWait(consumer);

        Object item = buffer[out];
        buffer[out] = null;
        out = (out + 1) % capacity;
        count--;

        mutex.semSignal();
        empty.semSignal();

        return item;
    }

    public String getId() { return id; }
    public int getCapacity() { return capacity; }
    public int getHostComputerId() { return hostComputerId; }
    public int getCount() { return count; }
    public Semaphore getMutex() { return mutex; }
    public Semaphore getEmpty() { return empty; }
    public Semaphore getFull() { return full; }

    // TODO: Implementar la verificacion de latencia de red  para accesos remotos
}
