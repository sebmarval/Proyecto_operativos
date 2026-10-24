package com.avilaos.gui;

import com.avilaos.metrics.BufferMetrics;
import com.avilaos.metrics.ComputerMetrics;
import com.avilaos.metrics.GlobalMetrics;
import com.avilaos.metrics.MetricsCollector;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

// Muestra las métricas ya calculadas por MetricsCollector (una columna por computador + global).
// No calcula nada: solo da formato. NaN significa "no aplica todavía" y se muestra como "—".
public class MetricsPanel extends JPanel {

    private static final String[] ROW_NAMES = {
        "Ciclos transcurridos",
        "Procesos terminados",
        "Completados a tiempo",
        "Deadlines vencidos",
        "Throughput (procesos por ciclo)",
        "Throughput (por 100 ciclos)",
        "Utilización de CPU (%)",
        "Tiempo de respuesta promedio (ciclos)",
        "Cumplimiento de deadlines (%)",
        "Equidad (índice de Jain)",
        "Tiempo promedio bloqueado en semáforos (ciclos)",
        "Elementos producidos (buffers alojados)",
        "Elementos consumidos (buffers alojados)"};

    private static final String[] BUFFER_COLUMNS = {"Buffer", "Anfitrión", "Elementos", "Producidos", "Consumidos"};

    private final MetricsCollector collector;
    private final int computers;
    private final DefaultTableModel metricsModel;
    private final DefaultTableModel buffersModel = readOnlyModel(BUFFER_COLUMNS);

    public MetricsPanel(MetricsCollector collector, int computers) {
        this.collector = collector;
        this.computers = computers;

        String[] columns = new String[computers + 2];
        columns[0] = "Métrica";
        for (int i = 0; i < computers; i++) {
            columns[i + 1] = "Computador " + i;
        }
        columns[computers + 1] = "Global";
        metricsModel = readOnlyModel(columns);

        JTable metricsTable = new JTable(metricsModel);
        metricsTable.setFillsViewportHeight(true);
        metricsTable.getColumnModel().getColumn(0).setPreferredWidth(300);
        JScrollPane metricsScroll = new JScrollPane(metricsTable);
        metricsScroll.setBorder(BorderFactory.createTitledBorder("Rendimiento por computador y global"));

        JTable buffersTable = new JTable(buffersModel);
        buffersTable.setFillsViewportHeight(true);
        JScrollPane buffersScroll = new JScrollPane(buffersTable);
        buffersScroll.setBorder(BorderFactory.createTitledBorder("Productor-consumidor: elementos por buffer"));

        setLayout(new GridLayout(2, 1, 6, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        add(metricsScroll);
        add(buffersScroll);
    }

    public void refresh() {
        String[][] rows = new String[ROW_NAMES.length][computers + 2];
        for (int r = 0; r < ROW_NAMES.length; r++) {
            rows[r][0] = ROW_NAMES[r];
        }
        for (int i = 0; i < computers; i++) {
            ComputerMetrics m = collector.getLatestComputerMetrics(i);
            int c = i + 1;
            rows[0][c] = String.valueOf(m.cycles());
            rows[1][c] = String.valueOf(m.terminated());
            rows[2][c] = String.valueOf(m.completedOnTime());
            rows[3][c] = String.valueOf(m.deadlineMissed());
            rows[4][c] = fmt(m.throughput(), 3);
            rows[5][c] = fmt(m.throughputPer100(), 1);
            rows[6][c] = fmt(m.cpuUtilization(), 1);
            rows[7][c] = fmt(m.avgResponseTime(), 2);
            rows[8][c] = fmt(m.deadlineCompliance(), 1);
            rows[9][c] = fmt(m.fairness(), 3);
            rows[10][c] = fmt(m.avgSemaphoreBlocked(), 2);
            rows[11][c] = String.valueOf(m.producedHosted());
            rows[12][c] = String.valueOf(m.consumedHosted());
        }

        GlobalMetrics g = collector.getLatestGlobalMetrics();
        int c = computers + 1;
        int produced = 0;
        int consumed = 0;
        for (int i = 0; i < g.buffers().length; i++) {
            produced += g.buffers()[i].produced();
            consumed += g.buffers()[i].consumed();
        }
        rows[0][c] = String.valueOf(g.cycles());
        rows[1][c] = String.valueOf(g.terminated());
        rows[2][c] = String.valueOf(g.completedOnTime());
        rows[3][c] = String.valueOf(g.deadlineMissed());
        rows[4][c] = fmt(g.throughput(), 3);
        rows[5][c] = fmt(g.throughputPer100(), 1);
        rows[6][c] = fmt(g.cpuUtilization(), 1);
        rows[7][c] = fmt(g.avgResponseTime(), 2);
        rows[8][c] = fmt(g.deadlineCompliance(), 1);
        rows[9][c] = fmt(g.fairness(), 3);
        rows[10][c] = fmt(g.avgSemaphoreBlocked(), 2);
        rows[11][c] = String.valueOf(produced);
        rows[12][c] = String.valueOf(consumed);

        metricsModel.setRowCount(0);
        for (int r = 0; r < rows.length; r++) {
            metricsModel.addRow(rows[r]);
        }

        buffersModel.setRowCount(0);
        for (int i = 0; i < g.buffers().length; i++) {
            BufferMetrics b = g.buffers()[i];
            buffersModel.addRow(new Object[]{b.id(), "PC" + b.hostComputerId(),
                b.count() + " / " + b.capacity(), b.produced(), b.consumed()});
        }
    }

    // las celdas no se editan: son solo lectura
    private static DefaultTableModel readOnlyModel(String[] columns) {
        return new DefaultTableModel(new Object[0][0], columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static String fmt(double value, int decimals) {
        return Double.isNaN(value) ? "—" : String.format("%." + decimals + "f", value);
    }
}
