package com.avilaos.gui;

import com.avilaos.enums.CPUMode;
import com.avilaos.enums.SchedulingPolicy;
import com.avilaos.hardware.CPU;
import com.avilaos.kernel.Kernel;
import com.avilaos.model.ProcessControlBlock;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

// Panel de UN computador. Solo lee (snapshots y getters del kernel) y manda una orden:
// cambiar la política. No mueve procesos ni calcula nada del sistema.
public class ComputerPanel extends JPanel {

    // todos los elementos del PCB que pide el enunciado (+ program counter)
    private static final String[] COLUMNS = {"ID", "Nombre", "Tipo", "Estado", "Prioridad",
        "Memoria", "Deadline", "Restante", "Program Counter", "Comp."};
    // en Bloqueados el Motivo va justo después del Estado para que se vea sin mover la barra
    private static final String[] BLOCKED_COLUMNS = {"ID", "Nombre", "Tipo", "Estado", "Motivo", "Prioridad",
        "Memoria", "Deadline", "Restante", "Program Counter", "Comp."};
    // en Terminados se agrega cómo acabó: completado o vencido por deadline
    private static final String[] TERMINATED_COLUMNS = {"ID", "Nombre", "Tipo", "Estado", "Prioridad",
        "Memoria", "Deadline", "Restante", "Program Counter", "Comp.", "Resultado"};
    // con AUTO_RESIZE_ALL_COLUMNS estos anchos sirven de proporción: las columnas se reparten el ancho del panel
    private static final int[] WIDTHS = {32, 55, 80, 90, 60, 60, 60, 60, 100, 50};
    private static final int[] BLOCKED_WIDTHS = {32, 55, 80, 90, 135, 60, 60, 60, 60, 100, 50};
    private static final int[] TERMINATED_WIDTHS = {32, 55, 80, 90, 60, 60, 60, 60, 100, 50, 110};

    private final Kernel kernel;

    // CPU
    private final JLabel processLabel = new JLabel("—");
    private final JLabel typeLabel = new JLabel("—");
    private final JLabel stateLabel = new JLabel("—");
    private final JLabel computerLabel = new JLabel("—");
    private final JLabel threadLabel = new JLabel("—");
    private final JLabel memoryLabel = new JLabel("—");
    private final JLabel pcLabel = new JLabel("—");
    private final JLabel marLabel = new JLabel("—");
    private final JLabel priorityLabel = new JLabel("—");
    private final JLabel deadlineLabel = new JLabel("—");
    private final JLabel remainingLabel = new JLabel("—");
    private final JLabel modeLabel = new JLabel("—");
    private final JLabel quantumUsedLabel = new JLabel("—");
    private final JLabel utilizationLabel = new JLabel("—");

    // RAM y política
    private final JProgressBar ramBar = new JProgressBar();
    private final JLabel policyLabel = new JLabel("—");
    private final JComboBox<String> policyBox = new JComboBox<>(policyNames());
    private final JTextField quantumField = new JTextField(4);

    // colas
    private final DefaultTableModel newModel = newModel(COLUMNS);
    private final DefaultTableModel readyModel = newModel(COLUMNS);
    private final DefaultTableModel blockedModel = newModel(BLOCKED_COLUMNS);
    private final DefaultTableModel terminatedModel = newModel(TERMINATED_COLUMNS);
    private final TitledBorder newBorder = new TitledBorder("Nuevos");
    private final TitledBorder readyBorder = new TitledBorder("Listos");
    private final TitledBorder blockedBorder = new TitledBorder("Bloqueados");
    private final TitledBorder terminatedBorder = new TitledBorder("Terminados");

    public ComputerPanel(Kernel kernel) {
        this.kernel = kernel;
        policyBox.setSelectedItem(kernel.getSchedulingPolicy().name());
        quantumField.setText(String.valueOf(kernel.getQuantum()));
        ramBar.setStringPainted(true);
        ramBar.setMinimum(0);
        ramBar.setMaximum(kernel.getRam().getCapacity());

        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(buildCpuPanel());
        north.add(buildRamPanel());
        north.add(buildPolicyPanel());
        add(north, BorderLayout.NORTH);

        // arriba Nuevos y Listos; abajo Bloqueados (más ancha por la columna Motivo) y Terminados
        JPanel upper = new JPanel(new GridLayout(1, 2, 6, 6));
        upper.add(queuePanel(newModel, newBorder, WIDTHS));
        upper.add(queuePanel(readyModel, readyBorder, WIDTHS));
        JPanel lower = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.weighty = 1;
        g.gridx = 0;
        g.weightx = 0.62;
        g.insets = new Insets(0, 0, 0, 3);
        lower.add(queuePanel(blockedModel, blockedBorder, BLOCKED_WIDTHS), g);
        g.gridx = 1;
        g.weightx = 0.38;
        g.insets = new Insets(0, 3, 0, 0);
        lower.add(queuePanel(terminatedModel, terminatedBorder, TERMINATED_WIDTHS), g);
        JPanel queues = new JPanel(new GridLayout(2, 1, 6, 6));
        queues.add(upper);
        queues.add(lower);
        add(queues, BorderLayout.CENTER);
    }

    // ---------------------------------------------------------------- construcción

    private JPanel buildCpuPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 8, 8, 4));
        panel.setBorder(BorderFactory.createTitledBorder("CPU - proceso en ejecución"));
        addField(panel, "Proceso:", processLabel);
        addField(panel, "Tipo:", typeLabel);
        addField(panel, "Estado:", stateLabel);
        addField(panel, "Computador:", computerLabel);
        addField(panel, "Memoria:", memoryLabel);
        addField(panel, "Prioridad:", priorityLabel);
        addField(panel, "Deadline (resta):", deadlineLabel);
        addField(panel, "Instr. restantes:", remainingLabel);
        addField(panel, "Program Counter:", pcLabel);
        addField(panel, "MAR:", marLabel);
        addField(panel, "Hilo (TCB):", threadLabel);
        addField(panel, "Modo:", modeLabel);
        addField(panel, "Quantum usado:", quantumUsedLabel);
        addField(panel, "Utilización CPU:", utilizationLabel);
        return panel;
    }

    private JPanel buildRamPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Memoria principal (RAM)"));
        panel.add(ramBar, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildPolicyPanel() {
        JButton apply = new JButton("Aplicar");
        apply.addActionListener(e -> applyPolicy());
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createTitledBorder("Planificación"));
        panel.add(new JLabel("En uso:"));
        panel.add(policyLabel);
        panel.add(new JLabel("   Cambiar a:"));
        panel.add(policyBox);
        panel.add(new JLabel("Quantum:"));
        panel.add(quantumField);
        panel.add(apply);
        return panel;
    }

    private static void addField(JPanel panel, String caption, JLabel value) {
        panel.add(new JLabel(caption));
        panel.add(value);
    }

    private static JScrollPane queuePanel(DefaultTableModel model, TitledBorder border, int[] widths) {
        // el tooltip muestra el texto completo de la celda si la columna quedó angosta
        JTable table = new JTable(model) {
            @Override
            public String getToolTipText(MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                int col = columnAtPoint(e.getPoint());
                if (row < 0 || col < 0) {
                    return null;
                }
                Object value = getValueAt(row, col);
                return value == null ? null : value.toString();
            }
        };
        // las columnas se reparten el ancho del panel (no hay barra horizontal)
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        // la tabla pinta todo el recuadro aunque tenga pocas filas (si no, queda gris debajo)
        table.setFillsViewportHeight(true);
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(border);
        return scroll;
    }

    private static DefaultTableModel newModel(String[] columns) {
        return new DefaultTableModel(new Object[0][0], columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    // ---------------------------------------------------------------- orden: cambiar política

    private void applyPolicy() {
        int quantum;
        try {
            quantum = Integer.parseInt(quantumField.getText().trim());
        } catch (NumberFormatException e) {
            showError("El quantum debe ser un número entero entre 1 y 100");
            return;
        }
        if (quantum < 1 || quantum > 100) {
            showError("El quantum debe estar entre 1 y 100 (recibido: " + quantum + ")");
            return;
        }
        SchedulingPolicy policy = SchedulingPolicy.valueOf((String) policyBox.getSelectedItem());
        try {
            kernel.setSchedulingPolicy(policy, quantum);
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Dato inválido", JOptionPane.ERROR_MESSAGE);
    }

    // ---------------------------------------------------------------- refresco (lo llama MainFrame con su Timer)

    public void refresh() {
        refreshCpu();
        refreshRam();
        fill(newModel, kernel.getNewSnapshot(), false, false);
        fill(readyModel, kernel.getReadySnapshot(), false, false);
        fill(blockedModel, kernel.getBlockedSnapshot(), true, false);
        fill(terminatedModel, kernel.getTerminatedSnapshot(), false, true);
        newBorder.setTitle("Nuevos (" + newModel.getRowCount() + ")");
        readyBorder.setTitle("Listos (" + readyModel.getRowCount() + ")");
        blockedBorder.setTitle("Bloqueados (" + blockedModel.getRowCount() + ")");
        terminatedBorder.setTitle("Terminados (" + terminatedModel.getRowCount() + ")");
        repaint();
    }

    private void refreshCpu() {
        CPU cpu = kernel.getCpu();
        ProcessControlBlock running = cpu.getRunning();
        if (running == null) {
            processLabel.setText("Ninguno (CPU libre)");
            typeLabel.setText("—");
            stateLabel.setText("—");
            computerLabel.setText("—");
            threadLabel.setText("—");
            memoryLabel.setText("—");
            pcLabel.setText("—");
            marLabel.setText("—");
            priorityLabel.setText("—");
            deadlineLabel.setText("—");
            remainingLabel.setText("—");
        } else {
            processLabel.setText("P" + running.getId() + " " + running.getName());
            typeLabel.setText(running.getType().name());
            stateLabel.setText(running.getState().name());
            computerLabel.setText("PC" + running.getAssignedComputerId());
            threadLabel.setText(running.getTcb().getThreadId());
            memoryLabel.setText(String.valueOf(running.getMemoryRequired()));
            pcLabel.setText(String.valueOf(running.getProgramCounter()));
            marLabel.setText(String.valueOf(running.getTcb().getMemoryAddressRegister()));
            priorityLabel.setText(String.valueOf(running.getPriority()));
            deadlineLabel.setText(String.valueOf(running.getDeadline()));
            remainingLabel.setText(String.valueOf(running.getRemainingTime()));
        }
        modeLabel.setText(cpu.getMode() == CPUMode.USER_MODE ? "Usuario" : "Sistema operativo");
        quantumUsedLabel.setText(kernel.getSchedulingPolicy() == SchedulingPolicy.ROUND_ROBIN
                ? cpu.getQuantumUsed() + " / " + kernel.getQuantum()
                : String.valueOf(cpu.getQuantumUsed()));
        utilizationLabel.setText(String.format("%.1f %%", cpu.getUtilizationPercent()));
        policyLabel.setText(kernel.getSchedulingPolicy()
                + (kernel.getSchedulingPolicy() == SchedulingPolicy.ROUND_ROBIN ? " (quantum " + kernel.getQuantum() + ")" : ""));
    }

    private void refreshRam() {
        int used = kernel.getRam().getUsed();
        int capacity = kernel.getRam().getCapacity();
        ramBar.setValue(used);
        ramBar.setString("Usada " + used + " / " + capacity + "   (libre " + kernel.getRam().getFree() + ")");
    }

    // Vuelve a llenar la tabla con la copia segura que entrega el kernel
    private static void fill(DefaultTableModel model, Object[] snapshot, boolean withReason, boolean withResult) {
        model.setRowCount(0);
        for (int i = 0; i < snapshot.length; i++) {
            ProcessControlBlock p = (ProcessControlBlock) snapshot[i];
            Object[] row = new Object[10 + (withReason ? 1 : 0) + (withResult ? 1 : 0)];
            int c = 0;
            row[c++] = "P" + p.getId();
            row[c++] = p.getName();
            row[c++] = p.getType();
            row[c++] = p.getState();
            if (withReason) {
                row[c++] = p.getBlockReason().name();
            }
            row[c++] = p.getPriority();
            row[c++] = p.getMemoryRequired();
            row[c++] = p.getDeadline();
            row[c++] = p.getRemainingTime();
            row[c++] = p.getProgramCounter();
            row[c++] = "PC" + p.getAssignedComputerId();
            if (withResult) {
                // restante 0 = terminó sus instrucciones; deadlineMissed = lo terminó el sistema por vencer
                row[c++] = p.isDeadlineMissed() ? "Deadline vencido" : "Completado";
            }
            model.addRow(row);
        }
    }

    private static String[] policyNames() {
        SchedulingPolicy[] values = SchedulingPolicy.values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }
}