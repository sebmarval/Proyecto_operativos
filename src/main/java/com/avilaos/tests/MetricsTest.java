/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.tests;

/**
 *
 * @author aleja
 */
import com.avilaos.enums.ProcessState;
import com.avilaos.enums.ProcessType;
import com.avilaos.enums.SchedulingPolicy;
import com.avilaos.kernel.EventLogger;
import com.avilaos.metrics.ComputerMetrics;
import com.avilaos.metrics.GlobalMetrics;
import com.avilaos.metrics.MetricsCalculator;
import com.avilaos.metrics.MetricsCollector;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.sync.BoundedBuffer;
import com.avilaos.system.Cluster;
 
// Prueba de las métricas. Se corre con Shift+F6 sobre este archivo.
public class MetricsTest {
 
    private static int passed = 0;
    private static int failed = 0;
 
    public static void main(String[] args) {
        testPureFunctions();
        testNoProcesses();
        testNormalScenario();
        testDeadlineMissed();
        testProducerConsumerMetrics();
        testCircularHistory();
        System.out.println("--------------------------------");
        System.out.println("Pruebas OK: " + passed + " | Fallidas: " + failed);
    }
 
    private static void testPureFunctions() {
        check("jain({1,1,1,1}) == 1.0", near(MetricsCalculator.jainIndex(new double[]{1, 1, 1, 1}), 1.0));
        check("jain({1,0,0,0}) == 0.25 (1/n)", near(MetricsCalculator.jainIndex(new double[]{1, 0, 0, 0}), 0.25));
        check("jain({}) es NaN", Double.isNaN(MetricsCalculator.jainIndex(new double[0])));
        check("jain({0,0}) == 1.0 (nadie recibió CPU)", near(MetricsCalculator.jainIndex(new double[]{0, 0}), 1.0));
        check("percentage(3,4) == 75.0", near(MetricsCalculator.percentage(3, 4), 75.0));
        check("percentage(0,0) == 0.0 (sin dividir entre 0)", near(MetricsCalculator.percentage(0, 0), 0.0));
        check("average({2,4,6}) == 4.0", near(MetricsCalculator.average(new int[]{2, 4, 6}), 4.0));
        check("average({}) es NaN", Double.isNaN(MetricsCalculator.average(new int[0])));
        check("throughput(5,10) == 0.5", near(MetricsCalculator.throughput(5, 10), 0.5));
        check("throughput(5,0) == 0.0", near(MetricsCalculator.throughput(5, 0), 0.0));
    }
 
    // Sin procesos: lo que no aplica da NaN y la utilización es 0
    private static void testNoProcesses() {
        Cluster cl = cluster();
        MetricsCollector mc = new MetricsCollector(cl);
        tick(cl, mc, 1);
        ComputerMetrics m = mc.computeComputer(0);
        check("sin procesos: respuesta, cumplimiento y equidad son NaN",
                Double.isNaN(m.avgResponseTime()) && Double.isNaN(m.deadlineCompliance())
                && Double.isNaN(m.fairness()));
        check("sin procesos: utilización 0 y throughput 0", near(m.cpuUtilization(), 0.0) && near(m.throughput(), 0.0));
    }
 
    // 3 procesos CPU_BOUND (2 en PC0, 1 en PC1) durante 30 ciclos
    private static void testNormalScenario() {
        Cluster cl = cluster();
        MetricsCollector mc = new MetricsCollector(cl);
        cl.submit(cpu("a", 0, 5, 9000));
        cl.submit(cpu("b", 0, 5, 9000));
        cl.submit(cpu("c", 1, 5, 9000));
        for (int c = 1; c <= 30; c++) {
            tick(cl, mc, c);
        }
        ComputerMetrics pc0 = mc.computeComputer(0);
        GlobalMetrics g = mc.computeGlobal();
        check("PC0: terminaron los 2 procesos a tiempo", pc0.terminated() == 2 && pc0.completedOnTime() == 2);
        check("global: terminaron los 3 procesos", g.terminated() == 3 && g.completedOnTime() == 3);
        check("throughput > 0", pc0.throughput() > 0 && g.throughput() > 0);
        check("throughput por 100 ciclos = throughput x 100", near(g.throughputPer100(), g.throughput() * 100.0));
        check("utilización entre 0 y 100",
                pc0.cpuUtilization() > 0 && pc0.cpuUtilization() <= 100
                && g.cpuUtilization() > 0 && g.cpuUtilization() <= 100);
        check("utilización global = promedio de los computadores",
                near(g.cpuUtilization(), (mc.computeComputer(0).cpuUtilization()
                        + mc.computeComputer(1).cpuUtilization()) / 2.0));
        check("cumplimiento de deadlines 100%", near(pc0.deadlineCompliance(), 100.0) && near(g.deadlineCompliance(), 100.0));
        check("tiempo de respuesta promedio definido (>= 0)", pc0.avgResponseTime() >= 0 && g.avgResponseTime() >= 0);
        check("equidad de Jain en (0, 1]", g.fairness() > 0 && g.fairness() <= 1.0 + 1e-9);
        check("historial con 30 muestras por computador",
                mc.getUtilizationHistory(0).length == 30 && mc.getUtilizationHistory(1).length == 30);
        check("las muestras cubren los ciclos 1..30", mc.getOldestCycle() == 1 && mc.getLatestCycle() == 30);
        check("las últimas métricas guardadas coinciden con el cálculo",
                mc.getLatestGlobalMetrics().terminated() == g.terminated());
    }
 
    // Proceso de 50 instrucciones con deadline 3: vence y baja el cumplimiento
    private static void testDeadlineMissed() {
        Cluster cl = cluster();
        MetricsCollector mc = new MetricsCollector(cl);
        cl.submit(cpu("lento", 0, 50, 3));
        for (int c = 1; c <= 10; c++) {
            tick(cl, mc, c);
        }
        ComputerMetrics m = mc.computeComputer(0);
        check("el proceso con deadline corto terminó (vencido)", m.terminated() == 1 && m.deadlineMissed() == 1);
        check("cumplimiento < 100% cuando vence un deadline",
                !Double.isNaN(m.deadlineCompliance()) && m.deadlineCompliance() < 100.0);
        check("un proceso vencido no cuenta en el throughput", near(m.throughput(), 0.0));
    }
 
    // Productor y consumidor en el mismo computador que el buffer
    private static void testProducerConsumerMetrics() {
        Cluster cl = cluster();
        MetricsCollector mc = new MetricsCollector(cl);
        BoundedBuffer buf = cl.createBuffer("B1", 2, 0);
        ProcessControlBlock prod = ProcessControlBlock.createProducer("prod", 0, 1, 10, 5000, buf, 3, 1);
        ProcessControlBlock cons = ProcessControlBlock.createConsumer("cons", 0, 1, 10, 5000, buf, 3, 1);
        cl.submit(prod);
        cl.submit(cons);
        int cycle = 1;
        while (cycle <= 300 && !(done(prod) && done(cons))) {
            tick(cl, mc, cycle++);
        }
        GlobalMetrics g = mc.computeGlobal();
        check("el productor y el consumidor terminaron", done(prod) && done(cons));
        check("hay 1 buffer en las métricas", g.buffers().length == 1);
        check("buffer: 3 producidos y 3 consumidos",
                g.buffers()[0].produced() == 3 && g.buffers()[0].consumed() == 3);
        check("PC0 (anfitrión) reporta lo producido/consumido",
                mc.computeComputer(0).producedHosted() == 3 && mc.computeComputer(0).consumedHosted() == 3);
        check("PC1 no aloja buffers: 0 producidos",
                mc.computeComputer(1).producedHosted() == 0);
        check("tiempo promedio bloqueado en semáforos definido (>= 0)",
                !Double.isNaN(g.avgSemaphoreBlocked()) && g.avgSemaphoreBlocked() >= 0);
    }
 
    // Después de 520 ciclos solo deben quedar las últimas 500 muestras
    private static void testCircularHistory() {
        Cluster cl = cluster();
        MetricsCollector mc = new MetricsCollector(cl);
        for (int c = 1; c <= 520; c++) {
            tick(cl, mc, c);
        }
        double[] h = mc.getUtilizationHistory(0);
        check("historial limitado a 500 muestras", h.length == MetricsCollector.HISTORY_SIZE);
        check("la muestra más vieja es el ciclo 21 y la más nueva el 520",
                mc.getOldestCycle() == 21 && mc.getLatestCycle() == 520);
        check("rechaza un computador inexistente en el historial", throwsIAE(() -> mc.getUtilizationHistory(9)));
    }
 
    // ---------------------------------------------------------------- auxiliares
 
    // Avanza los dos computadores un ciclo y luego avisa al colector, como hace el reloj
    private static void tick(Cluster cl, MetricsCollector mc, int cycle) {
        for (int i = 0; i < cl.getComputerCount(); i++) {
            cl.getComputer(i).onTick(cycle);
        }
        mc.onTick(cycle);
    }
 
    private static Cluster cluster() {
        return new Cluster(new int[]{100, 100}, SchedulingPolicy.FCFS, 2, 0, 0, EventLogger.SILENT);
    }
 
    private static ProcessControlBlock cpu(String name, int computer, int instructions, int deadline) {
        return new ProcessControlBlock(name, computer, ProcessType.CPU_BOUND, 1, 10, deadline, instructions);
    }
 
    private static boolean done(ProcessControlBlock p) {
        return p.getState() == ProcessState.TERMINATED;
    }
 
    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }
 
    private static boolean throwsIAE(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
 
    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("[OK]    " + name);
        } else {
            failed++;
            System.out.println("[FALLA] " + name);
        }
    }
}
