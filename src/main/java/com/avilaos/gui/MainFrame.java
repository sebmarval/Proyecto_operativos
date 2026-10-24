package com.avilaos.gui;

import com.avilaos.metrics.MetricsCollector;
import com.avilaos.system.Cluster;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.Timer;

// Ventana principal. Arriba, pestañas: una por computador + métricas + gráfica.
// Abajo, el panel global (reloj, buffers, log) siempre visible.
//
// Un Timer de Swing (hilo de la GUI) relee todo cada 100 ms. Solo se refresca la pestaña
// visible para no gastar tiempo en las demás. El reloj NO arranca solo: lo inicia el botón
// Iniciar de GlobalPanel.
public class MainFrame extends JFrame {

    private static final int REFRESH_MS = 100;

    private final Cluster cluster;
    private final MetricsCollector collector;
    private final JTabbedPane tabs = new JTabbedPane();
    private final SummaryPanel summaryPanel;
    private final ComputerPanel[] computerPanels;
    private final MetricsPanel metricsPanel;
    private final UtilizationChartPanel chartPanel;
    private final GlobalPanel globalPanel;
    private final Timer timer;

    public MainFrame(Cluster cluster, JTextArea logArea) {
        super("ÁvilaOS - Simulación");
        this.cluster = cluster;
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // el reloj llama al colector al final de cada ciclo, con todos los computadores quietos
        collector = new MetricsCollector(cluster);
        cluster.setAfterTickObserver(collector);

        int n = cluster.getComputerCount();
        // la primera pestaña muestra todos los computadores a la vez
        summaryPanel = new SummaryPanel(cluster);
        tabs.addTab("Resumen (todos)", summaryPanel);
        computerPanels = new ComputerPanel[n];
        for (int i = 0; i < n; i++) {
            computerPanels[i] = new ComputerPanel(cluster.getComputer(i).getKernel());
            tabs.addTab("Computador " + i, computerPanels[i]);
        }
        metricsPanel = new MetricsPanel(collector, n);
        chartPanel = new UtilizationChartPanel(collector, n);
        tabs.addTab("Métricas", metricsPanel);
        tabs.addTab("Gráfica de utilización", chartPanel);

        globalPanel = new GlobalPanel(cluster, logArea);
        globalPanel.setPreferredSize(new Dimension(0, 270));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, globalPanel);
        split.setResizeWeight(0.72);
        setLayout(new BorderLayout());
        add(split, BorderLayout.CENTER);
        setSize(1250, 920);
        setLocationRelativeTo(null);

        timer = new Timer(REFRESH_MS, e -> refresh());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                timer.stop();
                cluster.stop();
            }
        });
        timer.start();
    }

    private void refresh() {
        globalPanel.refresh();
        // orden de pestañas: 0 = resumen, 1..n = computadores, n+1 = métricas, n+2 = gráfica
        int selected = tabs.getSelectedIndex();
        int n = computerPanels.length;
        if (selected == 0) {
            summaryPanel.refresh();
        } else if (selected <= n) {
            computerPanels[selected - 1].refresh();
        } else if (selected == n + 1) {
            metricsPanel.refresh();
        } else {
            chartPanel.refresh();
        }
    }
}