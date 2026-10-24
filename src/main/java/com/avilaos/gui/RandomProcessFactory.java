package com.avilaos.gui;

import com.avilaos.enums.ProcessType;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.sync.BoundedBuffer;
import com.avilaos.system.Cluster;

// Crea procesos con valores al azar para probar el simulador.
public final class RandomProcessFactory {

    private static int counter = 0;

    private RandomProcessFactory() {
    }

    private static int rnd(int min, int max) {
        return min + (int) (Math.random() * (max - min + 1));
    }

    public static ProcessControlBlock create(Cluster cluster) {
        counter++;
        String name = "Auto" + counter;
        int computer = rnd(0, cluster.getComputerCount() - 1);
        int ram = cluster.getComputer(computer).getKernel().getRam().getCapacity();
        // la memoria llega hasta la mitad de la RAM: así algunos procesos no caben
        // y esperan en la cola de Nuevos
        int memory = rnd(8, Math.max(16, ram / 2));
        int priority = rnd(0, 9);
        int deadline = rnd(80, 500);

        Object[] buffers = cluster.getBuffersSnapshot();
        ProcessType[] types = ProcessType.values();
        ProcessType type = types[rnd(0, types.length - 1)];
        boolean needsBuffer = type == ProcessType.PRODUCER || type == ProcessType.CONSUMER;
        if (needsBuffer && buffers.length == 0) {
            type = rnd(0, 1) == 0 ? ProcessType.CPU_BOUND : ProcessType.IO_BOUND;
        }

        if (type == ProcessType.CPU_BOUND) {
            return new ProcessControlBlock(name, computer, type, priority, memory, deadline, rnd(10, 40));
        }
        if (type == ProcessType.IO_BOUND) {
            ProcessControlBlock io = new ProcessControlBlock(name, computer, type, priority,
                    memory, deadline, rnd(10, 40));
            io.configureIO(rnd(2, 5), rnd(2, 6));
            return io;
        }
        BoundedBuffer buffer = (BoundedBuffer) buffers[rnd(0, buffers.length - 1)];
        int items = rnd(3, 8);
        int workCycles = rnd(1, 3);
        if (type == ProcessType.PRODUCER) {
            return ProcessControlBlock.createProducer(name, computer, priority, memory, deadline,
                    buffer, items, workCycles);
        }
        return ProcessControlBlock.createConsumer(name, computer, priority, memory, deadline,
                buffer, items, workCycles);
    }
}