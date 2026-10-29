package com.avilaos.config;

// Datos de un buffer: id, capacidad y en qué computador vive.
public class BufferConfig {

    public String id;
    public int capacity;
    public int hostComputerId;

    public BufferConfig() {
    }

    public BufferConfig(String id, int capacity, int hostComputerId) {
        this.id = id;
        this.capacity = capacity;
        this.hostComputerId = hostComputerId;
    }
}
