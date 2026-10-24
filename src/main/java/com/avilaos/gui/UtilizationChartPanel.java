package com.avilaos.gui;

import com.avilaos.metrics.MetricsCollector;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

// Gráfica de utilización de CPU (%) contra el ciclo, una línea por computador, en un mismo gráfico
// (punto 8 del enunciado). Lee el historial circular de MetricsCollector y agrega solo los
// puntos nuevos. La utilización es la acumulada de CPU.getUtilizationPercent(), por eso la curva es suave.
public class UtilizationChartPanel extends JPanel {

    private final MetricsCollector collector;
    private final XYSeries[] series;
    private int lastCycleDrawn = 0;

    public UtilizationChartPanel(MetricsCollector collector, int computers) {
        this.collector = collector;
        series = new XYSeries[computers];
        XYSeriesCollection dataset = new XYSeriesCollection();
        for (int i = 0; i < computers; i++) {
            series[i] = new XYSeries("Computador " + i);
            // mismo tope que el historial del colector
            series[i].setMaximumItemCount(MetricsCollector.HISTORY_SIZE);
            dataset.addSeries(series[i]);
        }
        JFreeChart chart = ChartFactory.createXYLineChart("Utilización de CPU por computador",
                "Ciclo", "Utilización (%)", dataset, PlotOrientation.VERTICAL, true, true, false);
        chart.getXYPlot().getRangeAxis().setRange(0, 100);
        setLayout(new BorderLayout());
        add(new ChartPanel(chart), BorderLayout.CENTER);
    }

    public void refresh() {
        // una sola lectura de todos los computadores: así las líneas quedan alineadas en el mismo ciclo
        MetricsCollector.UtilizationSnapshot snap = collector.getUtilizationSnapshot();
        if (snap.latestCycle() <= lastCycleDrawn) {
            return;
        }
        for (int id = 0; id < series.length; id++) {
            double[] values = snap.values()[id];
            for (int k = 0; k < values.length; k++) {
                int cycle = snap.oldestCycle() + k;
                if (cycle > lastCycleDrawn) {
                    series[id].add(cycle, values[k], false);   // sin notificar en cada punto
                }
            }
            series[id].fireSeriesChanged();
        }
        lastCycleDrawn = snap.latestCycle();
    }
}
