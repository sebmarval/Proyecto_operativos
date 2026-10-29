package com.avilaos.gui;

import com.avilaos.enums.CPUMode;
import com.avilaos.enums.SchedulingPolicy;
import com.avilaos.hardware.CPU;
import com.avilaos.kernel.Kernel;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.system.Cluster;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.TitledBorder;

// Vista compacta de TODOS los computadores a la vez (una tarjeta por computador).
// Complementa a las pestañas de detalle (ComputerPanel): aquí no están todas las columnas del PCB,
// pero sí lo esencial de cada máquina: proceso en CPU, modo, política, RAM y el contenido de las
// cuatro colas. Igual que el resto de la GUI: solo lee snapshots del kernel, no modifica nada.
public class SummaryPanel extends JPanel {

    private final Card[] cards;

    public SummaryPanel(Cluster cluster) {
        int n = cluster.getComputerCount();
        cards = new Card[n];
        // hasta 4 computadores en una fila; si hay más, se acomodan en varias filas
        setLayout(new GridLayout(0, Math.min(n, 4), 6, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        for (int i = 0; i < n; i++) {
            cards[i] = new Card(i, cluster.getComputer(i).getKernel());
            add(cards[i]);
        }
    }

    public void refresh() {
        for (int i = 0; i < cards.length; i++) {
            cards[i].refresh();
        }
    }

    // ---------------------------------------------------------------- una tarjeta por computador

    private static class Card extends JPanel {

        private final Kernel kernel;
        private final JLabel policyLabel = new JLabel("—");
        private final JLabel cpuLabel = new JLabel("—");
        private final JLabel detailLabel = new JLabel("—");
        private final JProgressBar ramBar = new JProgressBar();
        private final JTextArea[] areas = new JTextArea[4];
        private final TitledBorder[] borders = new TitledBorder[4];
        private final String[] titles = {"Nuevos", "Listos", "Bloqueados", "Terminados"};
        private final String[] lastText = new String[4];

        Card(int id, Kernel kernel) {
            this.kernel = kernel;
            setLayout(new BorderLayout(4, 4));
            setBorder(BorderFactory.createTitledBorder("Computador " + id));

            ramBar.setStringPainted(true);
            ramBar.setMaximum(kernel.getRam().getCapacity());

            JPanel top = new JPanel();
            top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
            top.add(policyLabel);
            top.add(cpuLabel);
            top.add(detailLabel);
            top.add(ramBar);
            add(top, BorderLayout.NORTH);

            JPanel queues = new JPanel(new GridLayout(4, 1, 4, 4));
            for (int q = 0; q < 4; q++) {
                areas[q] = new JTextArea();
                areas[q].setEditable(false);
                borders[q] = new TitledBorder(titles[q]);
                JScrollPane scroll = new JScrollPane(areas[q]);
                scroll.setBorder(borders[q]);
                queues.add(scroll);
            }
            add(queues, BorderLayout.CENTER);
        }

        void refresh() {
            CPU cpu = kernel.getCpu();
            ProcessControlBlock running = cpu.getRunning();
            String mode = cpu.getMode() == CPUMode.USER_MODE ? "Usuario" : "Sistema operativo";
            SchedulingPolicy policy = kernel.getSchedulingPolicy();
            policyLabel.setText("Política: " + policy
                    + (policy == SchedulingPolicy.ROUND_ROBIN ? " (quantum " + kernel.getQuantum() + ")" : "")
                    + "   |   CPU: " + String.format("%.1f %%", cpu.getUtilizationPercent()));
            if (running == null) {
                cpuLabel.setText("En CPU: ninguno");
                detailLabel.setText("Modo: " + mode);
            } else {
                cpuLabel.setText("En CPU: P" + running.getId() + " " + running.getName());
                detailLabel.setText("PC " + running.getProgramCounter() + " | prioridad " + running.getPriority()
                        + " | deadline " + running.getDeadline() + " | " + mode);
            }
            int used = kernel.getRam().getUsed();
            ramBar.setValue(used);
            ramBar.setString("RAM " + used + " / " + kernel.getRam().getCapacity());

            show(0, kernel.getNewSnapshot(), false, false);
            show(1, kernel.getReadySnapshot(), false, false);
            show(2, kernel.getBlockedSnapshot(), true, false);
            show(3, kernel.getTerminatedSnapshot(), false, true);
        }

        // Una línea por proceso. El texto solo se reemplaza si cambió: si se reescribiera siempre,
        // el scroll de las listas largas (Terminados) volvería arriba cada 100 ms.
        private void show(int queue, Object[] snapshot, boolean withReason, boolean withResult) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < snapshot.length; i++) {
                ProcessControlBlock p = (ProcessControlBlock) snapshot[i];
                sb.append("P").append(p.getId()).append(" ").append(p.getName());
                if (withReason) {
                    sb.append(" - ").append(p.getBlockReason().name());
                }
                if (withResult) {
                    sb.append(p.isDeadlineMissed() ? " - Deadline vencido" : " - Completado");
                }
                sb.append('\n');
            }
            String text = sb.toString();
            borders[queue].setTitle(titles[queue] + " (" + snapshot.length + ")");
            if (!text.equals(lastText[queue])) {
                areas[queue].setText(text);
                areas[queue].setCaretPosition(0);
                lastText[queue] = text;
            }
            repaint();
        }
    }
}