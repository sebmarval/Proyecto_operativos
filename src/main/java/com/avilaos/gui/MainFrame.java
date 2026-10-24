package com.avilaos.gui;

import com.avilaos.system.Cluster;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.Timer;

// Ventana principal: una pestaña por computador. Un Timer de Swing relee todo cada 100 ms.
// Al abrirse arranca el reloj (provisional: en la pieza del GlobalPanel esto pasa a botones).
public class MainFrame extends JFrame {

    private static final int REFRESH_MS = 100;

    private final Cluster cluster;
    private final ComputerPanel[] panels;
    private final JLabel cycleLabel = new JLabel("Ciclo actual: 0");
    private final Timer timer;

    public MainFrame(Cluster cluster) {
        super("ÁvilaOS - Simulación");
        this.cluster = cluster;
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        int n = cluster.getComputerCount();
        panels = new ComputerPanel[n];
        JTabbedPane tabs = new JTabbedPane();
        for (int i = 0; i < n; i++) {
            panels[i] = new ComputerPanel(cluster.getComputer(i).getKernel());
            tabs.addTab("Computador " + i, panels[i]);
        }

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(cycleLabel);
        JButton addOne = new JButton("Crear proceso aleatorio");
        JButton addFive = new JButton("Crear 5 aleatorios");
        addOne.addActionListener(e -> addRandomProcesses(1));
        addFive.addActionListener(e -> addRandomProcesses(5));
        top.add(addOne);
        top.add(addFive);
        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        setSize(1150, 780);
        setLocationRelativeTo(null);

        timer = new Timer(REFRESH_MS, e -> refresh());

        // al cerrar la ventana se detiene el refresco y el reloj del clúster
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                timer.stop();
                cluster.stop();
            }
        });

        timer.start();
        cluster.start();
    }

    // Manda procesos al azar al clúster para probar (la GUI solo envía la orden submit)
    private void addRandomProcesses(int count) {
        for (int i = 0; i < count; i++) {
            try {
                cluster.submit(RandomProcessFactory.create(cluster));
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(),
                        "No se pudo crear el proceso", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
    }

    private void refresh() {
        cycleLabel.setText("Ciclo actual: " + cluster.getCurrentCycle());
        for (int i = 0; i < panels.length; i++) {
            panels[i].refresh();
        }
    }
}