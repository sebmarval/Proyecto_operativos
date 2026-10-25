package com.avilaos.config;

import com.avilaos.enums.ProcessType;
import com.avilaos.kernel.EventLogger;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.model.ProcessIdGenerator;
import com.avilaos.sync.BoundedBuffer;
import com.avilaos.system.Cluster;

// Arma un Cluster completo a partir de una SimulationConfig.
// El reloj queda sin arrancar: eso lo decide la GUI con cluster.start().
public final class ClusterFactory {

    private ClusterFactory() {
    }

    public static Cluster build(SimulationConfig cfg, EventLogger logger) {
        ConfigValidator.validate(cfg);

        // cada simulación nueva empieza los IDs de proceso desde 1
        ProcessIdGenerator.reset();

        int n = cfg.computers.length;
        int[] rams = new int[n];
        for (int i = 0; i < n; i++) {
            rams[i] = cfg.computers[i].ramCapacity;
        }
        Cluster cluster = new Cluster(rams,
                ConfigValidator.parsePolicy(cfg.computers[0].policy), cfg.computers[0].quantum,
                cfg.cycleDurationMs, cfg.networkLatency, logger);

        // el constructor aplica la política del PC0 a todos; aquí se pone la de cada uno
        for (int i = 0; i < n; i++) {
            cluster.getComputer(i).getKernel().setSchedulingPolicy(
                    ConfigValidator.parsePolicy(cfg.computers[i].policy), cfg.computers[i].quantum);
        }

        BufferConfig[] bufferCfg = cfg.buffers == null ? new BufferConfig[0] : cfg.buffers;
        BoundedBuffer[] buffers = new BoundedBuffer[bufferCfg.length];
        for (int i = 0; i < bufferCfg.length; i++) {
            buffers[i] = cluster.createBuffer(bufferCfg[i].id, bufferCfg[i].capacity, bufferCfg[i].hostComputerId);
        }

        ProcessConfig[] processCfg = cfg.processes == null ? new ProcessConfig[0] : cfg.processes;
        for (int i = 0; i < processCfg.length; i++) {
            cluster.submit(buildProcess(cluster, processCfg[i], bufferCfg, buffers));
        }
        return cluster;
    }

    private static ProcessControlBlock buildProcess(Cluster cluster, ProcessConfig p,
            BufferConfig[] bufferCfg, BoundedBuffer[] buffers) {
        // -1 = automático
        int computer = p.computerId == -1 ? cluster.pickComputerAutomatically(p.memory) : p.computerId;
        ProcessType type = ConfigValidator.parseType(p.type);

        switch (type) {
            case CPU_BOUND:
                return new ProcessControlBlock(p.name, computer, type, p.priority,
                        p.memory, p.deadline, p.instructions);
            case IO_BOUND:
                ProcessControlBlock io = new ProcessControlBlock(p.name, computer, type, p.priority,
                        p.memory, p.deadline, p.instructions);
                io.configureIO(p.ioEvery, p.ioDuration);
                return io;
            case PRODUCER:
                return ProcessControlBlock.createProducer(p.name, computer, p.priority, p.memory,
                        p.deadline, findBuffer(bufferCfg, buffers, p.bufferId), p.items, p.workCycles);
            case CONSUMER:
                return ProcessControlBlock.createConsumer(p.name, computer, p.priority, p.memory,
                        p.deadline, findBuffer(bufferCfg, buffers, p.bufferId), p.items, p.workCycles);
            default:
                throw new IllegalArgumentException("Tipo no soportado: " + type);
        }
    }

    private static BoundedBuffer findBuffer(BufferConfig[] bufferCfg, BoundedBuffer[] buffers, String id) {
        for (int i = 0; i < bufferCfg.length; i++) {
            if (bufferCfg[i].id.equals(id)) {
                return buffers[i];
            }
        }
        throw new IllegalArgumentException("El buffer '" + id + "' no existe");
    }
}

