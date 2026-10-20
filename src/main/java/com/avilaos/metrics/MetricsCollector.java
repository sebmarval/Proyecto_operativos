/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.metrics;
 
import com.avilaos.enums.ProcessState;
import com.avilaos.enums.ProcessType;
import com.avilaos.hardware.ClockListener;
import com.avilaos.kernel.Kernel;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.sync.BoundedBuffer;
import com.avilaos.sync.BufferStatus;
import com.avilaos.system.Cluster;
import java.util.concurrent.Semaphore;
 
// Calcula las métricas al final de cada ciclo y guarda el historial de utilización
// para la gráfica. Se registra con cluster.setAfterTickObserver(collector).
public class MetricsCollector implements ClockListener {
 
    public static final int HISTORY_SIZE = 500;
 
    private final Cluster cluster;
    private final double[][] history;   // una fila por computador, circular
    private int next;                   // dónde se escribe la próxima muestra
    private int size;                   // cuántas muestras hay guardadas
    private int lastCycle;
    private ComputerMetrics[] latestComputers;
    private GlobalMetrics latestGlobal;
 
    // onTick corre en el hilo del reloj y la GUI lee desde el hilo de Swing
    private final Semaphore lock = new Semaphore(1);
 
    public MetricsCollector(Cluster cluster) {
        if (cluster == null) {
            throw new IllegalArgumentException("El colector necesita un clúster");
        }
        this.cluster = cluster;
        this.history = new double[cluster.getComputerCount()][HISTORY_SIZE];
    }
 
    // Lo llama el reloj cuando todos los computadores terminaron el ciclo
    @Override
    public void onTick(int cycle) {
        int n = cluster.getComputerCount();
        ComputerMetrics[] computers = new ComputerMetrics[n];
        for (int i = 0; i < n; i++) {
            computers[i] = computeComputer(i);
        }
        GlobalMetrics global = computeGlobal();
 
        lock.acquireUninterruptibly();
        try {
            for (int i = 0; i < n; i++) {
                history[i][next] = computers[i].cpuUtilization();
            }
            next = (next + 1) % HISTORY_SIZE;
            if (size < HISTORY_SIZE) {
                size++;
            }
            lastCycle = cycle;
            latestComputers = computers;
            latestGlobal = global;
        } finally {
            lock.release();
        }
    }
 
    // Métricas de un computador. Los ciclos se sacan del kernel, no del reloj,
    // para que también funcione cuando las pruebas avanzan los computadores a mano.
    public ComputerMetrics computeComputer(int id) {
        Kernel kernel = cluster.getComputer(id).getKernel();
        Stats s = analyze(collect(kernel));
        int cycles = kernel.getCurrentCycle();
 
        int produced = 0;
        int consumed = 0;
        BufferMetrics[] buffers = collectBuffers();
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i].hostComputerId() == id) {
                produced += buffers[i].produced();
                consumed += buffers[i].consumed();
            }
        }
        double throughput = MetricsCalculator.throughput(s.onTime, cycles);
        return new ComputerMetrics(id, cycles, s.terminated, s.onTime, s.terminated - s.onTime,
                throughput, throughput * 100.0, kernel.getCpu().getUtilizationPercent(),
                s.avgResponse, s.compliance, s.fairness, s.avgSemaphoreBlocked, produced, consumed);
    }
 
    // Métricas de todo el sistema: junta los procesos de todos los computadores
    public GlobalMetrics computeGlobal() {
        int n = cluster.getComputerCount();
        ProcessControlBlock[][] perComputer = new ProcessControlBlock[n][];
        int total = 0;
        int cycles = 0;
        double utilizationSum = 0.0;
        for (int i = 0; i < n; i++) {
            Kernel kernel = cluster.getComputer(i).getKernel();
            perComputer[i] = collect(kernel);
            total += perComputer[i].length;
            if (kernel.getCurrentCycle() > cycles) {
                cycles = kernel.getCurrentCycle();
            }
            utilizationSum += kernel.getCpu().getUtilizationPercent();
        }
        ProcessControlBlock[] all = new ProcessControlBlock[total];
        int pos = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < perComputer[i].length; j++) {
                all[pos++] = perComputer[i][j];
            }
        }
        Stats s = analyze(all);
        double throughput = MetricsCalculator.throughput(s.onTime, cycles);
        return new GlobalMetrics(n, cycles, s.terminated, s.onTime, s.terminated - s.onTime,
                throughput, throughput * 100.0, utilizationSum / n,
                s.avgResponse, s.compliance, s.fairness, s.avgSemaphoreBlocked, collectBuffers());
    }
 
    // Últimas métricas guardadas. Si aún no hubo ciclos se calculan en el momento.
    public ComputerMetrics getLatestComputerMetrics(int id) {
        lock.acquireUninterruptibly();
        try {
            if (latestComputers != null && id >= 0 && id < latestComputers.length) {
                return latestComputers[id];
            }
        } finally {
            lock.release();
        }
        return computeComputer(id);
    }
 
    public GlobalMetrics getLatestGlobalMetrics() {
        lock.acquireUninterruptibly();
        try {
            if (latestGlobal != null) {
                return latestGlobal;
            }
        } finally {
            lock.release();
        }
        return computeGlobal();
    }
 
    // Copia del historial del computador, de la muestra más vieja a la más nueva
    public double[] getUtilizationHistory(int id) {
        if (id < 0 || id >= history.length) {
            throw new IllegalArgumentException("No existe el computador " + id);
        }
        lock.acquireUninterruptibly();
        try {
            double[] copy = new double[size];
            // si el arreglo ya se llenó, la más vieja está en 'next'
            int start = (size < HISTORY_SIZE) ? 0 : next;
            for (int i = 0; i < size; i++) {
                copy[i] = history[id][(start + i) % HISTORY_SIZE];
            }
            return copy;
        } finally {
            lock.release();
        }
    }
 
    public int getLatestCycle() {
        lock.acquireUninterruptibly();
        try {
            return lastCycle;
        } finally {
            lock.release();
        }
    }
 
    // Las muestras son ciclos seguidos, así que la más vieja se saca restando
    public int getOldestCycle() {
        lock.acquireUninterruptibly();
        try {
            return size == 0 ? 0 : lastCycle - size + 1;
        } finally {
            lock.release();
        }
    }
 
    // ---------------------------------------------------------------- auxiliares
 
    private record Stats(int terminated, int onTime, double avgResponse, double compliance,
            double fairness, double avgSemaphoreBlocked) {
    }
 
    // Recorre los procesos una sola vez y saca todos los números
    private static Stats analyze(ProcessControlBlock[] all) {
        int terminated = 0;
        int onTime = 0;
        int[] response = new int[all.length];
        int responseN = 0;
        double[] shares = new double[all.length];
        int sharesN = 0;
        int[] semaphoreBlocked = new int[all.length];
        int semaphoreN = 0;
 
        for (int i = 0; i < all.length; i++) {
            ProcessControlBlock p = all[i];
            if (p.getState() == ProcessState.TERMINATED) {
                terminated++;
                if (!p.isDeadlineMissed()) {
                    onTime++;
                }
                // para Jain: ciclos de CPU que usó / tiempo total en el sistema
                int turnaround = p.getTurnaroundTime();
                if (turnaround > 0) {
                    shares[sharesN++] = (double) p.getCpuCycles() / turnaround;
                }
            }
            if (p.getResponseTime() >= 0) {
                response[responseN++] = p.getResponseTime();
            }
            if (p.getType() == ProcessType.PRODUCER || p.getType() == ProcessType.CONSUMER) {
                semaphoreBlocked[semaphoreN++] = p.getSemaphoreBlockedCycles();
            }
        }
        double compliance = terminated == 0 ? Double.NaN : MetricsCalculator.percentage(onTime, terminated);
        return new Stats(terminated, onTime,
                MetricsCalculator.average(trim(response, responseN)),
                compliance,
                MetricsCalculator.jainIndex(trim(shares, sharesN)),
                MetricsCalculator.average(trim(semaphoreBlocked, semaphoreN)));
    }
 
    // Todos los procesos del computador: nuevos, listos, bloqueados, terminados y el que está en CPU
    private static ProcessControlBlock[] collect(Kernel kernel) {
        Object[] a = kernel.getNewSnapshot();
        Object[] b = kernel.getReadySnapshot();
        Object[] c = kernel.getBlockedSnapshot();
        Object[] d = kernel.getTerminatedSnapshot();
        ProcessControlBlock running = kernel.getCpu().getRunning();
        int total = a.length + b.length + c.length + d.length + (running != null ? 1 : 0);
        ProcessControlBlock[] all = new ProcessControlBlock[total];
        int pos = 0;
        pos = copyInto(all, pos, a);
        pos = copyInto(all, pos, b);
        pos = copyInto(all, pos, c);
        pos = copyInto(all, pos, d);
        if (running != null) {
            all[pos] = running;
        }
        return all;
    }
 
    private static int copyInto(ProcessControlBlock[] target, int pos, Object[] source) {
        for (Object source1 : source) {
            target[pos++] = (ProcessControlBlock) source1;
        }
        return pos;
    }
 
    private BufferMetrics[] collectBuffers() {
        Object[] raw = cluster.getBuffersSnapshot();
        BufferMetrics[] result = new BufferMetrics[raw.length];
        for (int i = 0; i < raw.length; i++) {
            BufferStatus st = ((BoundedBuffer) raw[i]).snapshot();
            result[i] = new BufferMetrics(st.id(), st.hostComputerId(), st.count(),
                    st.capacity(), st.produced(), st.consumed());
        }
        return result;
    }
 
    // Recorta el arreglo a los primeros 'length' elementos
    private static int[] trim(int[] source, int length) {
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            result[i] = source[i];
        }
        return result;
    }
 
    private static double[] trim(double[] source, int length) {
        double[] result = new double[length];
        for (int i = 0; i < length; i++) {
            result[i] = source[i];
        }
        return result;
    }
}