package com.avilaos.config;

// Datos de un proceso. Según el tipo solo se usan algunos campos:
//  CPU_BOUND: instructions
//  IO_BOUND: instructions, ioEvery, ioDuration
//  PRODUCER / CONSUMER: bufferId, items, workCycles (las instrucciones se calculan solas)
// computerId = -1 significa que lo asigna el sistema automáticamente.
public class ProcessConfig {

    public String name;
    public String type;
    public int computerId;
    public int priority;
    public int memory;
    public int deadline;
    public int instructions;
    public int ioEvery;
    public int ioDuration;
    public String bufferId;
    public int items;
    public int workCycles;

    public ProcessConfig() {
    }
}
