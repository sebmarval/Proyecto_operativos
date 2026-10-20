package com.avilaos.config;

// Toda la configuración de una simulación. Gson llena estos campos directo desde el JSON.
public class SimulationConfig {

    public int cycleDurationMs;
    public int networkLatency;
    public ComputerConfig[] computers;
    public BufferConfig[] buffers;
    public ProcessConfig[] processes;

    public SimulationConfig() {
    }
}
