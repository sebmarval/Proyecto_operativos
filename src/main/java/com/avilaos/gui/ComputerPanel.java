package com.avilaos.gui;

import com.avilaos.enums.BlockReason;
import com.avilaos.enums.CPUMode;
import com.avilaos.enums.SchedulingPolicy;
import com.avilaos.hardware.CPU;
import com.avilaos.kernel.Kernel;
import com.avilaos.model.ProcessControlBlock;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
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

    private static final String[] COLUMNS = {"ID", "Nombre", "Tipo", "Estado", "Prioridad",
        "Memoria", "Deadline", "Restante", "PC"};
    private static final String[] BLOCKED_COLUMNS = {"ID", "Nombre", "Tipo", "Estado", "Prioridad",
        "Memoria", "Deadline", "Restante", "PC", "Motivo"};

    private final Kernel kernel;

    // CPU
    private final JLabel processLabel = new JLabel("—");
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
    private final DefaultTableModel terminatedModel = newModel(COLUMNS);
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

        JPanel queues = new JPanel(new GridLayout(2, 2, 6, 6));
        queues.add(queuePanel(newModel, newBorder));
        queues.add(queuePanel(readyModel, readyBorder));
        queues.add(queuePanel(blockedModel, blockedBorder));
        queues.add(queuePanel(terminatedModel, terminatedBorder));
        add(queues, BorderLayout.CENTER);
    }

    // ---------------------------------------------------------------- construcción

    private JPanel buildCpuPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 6, 8, 4));
        panel.setBorder(BorderFactory.createTitledBorder("CPU"));
        addField(panel, "Proceso:", processLabel);
        addField(panel, "Program Counter:", pcLabel);
        addField(panel, "MAR:", marLabel);
        addField(panel, "Prioridad:", priorityLabel);
        addField(panel, "Deadline (ciclos que quedan):", deadlineLabel);
        addField(panel, "Instrucciones restantes:", remainingLabel);
        addField(panel, "Modo:", modeLabel);
        addField(panel, "Quantum usado:", quantumUsedLabel);
        addField(panel, "Utilización de CPU:", utilizationLabel);
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

    private static JScrollPane queuePanel(DefaultTableModel model, TitledBorder border) {
        JTable table = new JTable(model);
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
        fill(newModel, kernel.getNewSnapshot(), false);
        fill(readyModel, kernel.getReadySnapshot(), false);
        fill(blockedModel, kernel.getBlockedSnapshot(), true);
        fill(terminatedModel, kernel.getTerminatedSnapshot(), false);
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
            pcLabel.setText("—");
            marLabel.setText("—");
            priorityLabel.setText("—");
            deadlineLabel.setText("—");
            remainingLabel.setText("—");
        } else {
            processLabel.setText("P" + running.getId() + " " + running.getName());
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
    private static void fill(DefaultTableModel model, Object[] snapshot, boolean withReason) {
        model.setRowCount(0);
        for (int i = 0; i < snapshot.length; i++) {
            ProcessControlBlock p = (ProcessControlBlock) snapshot[i];
            Object[] row = new Object[withReason ? 10 : 9];
            row[0] = "P" + p.getId();
            row[1] = p.getName();
            row[2] = p.getType();
            row[3] = p.getState();
            row[4] = p.getPriority();
            row[5] = p.getMemoryRequired();
            row[6] = p.getDeadline();
            row[7] = p.getRemainingTime();
            row[8] = p.getProgramCounter();
            if (withReason) {
                row[9] = reasonText(p.getBlockReason());
            }
            model.addRow(row);
        }
    }

    private static String reasonText(BlockReason reason) {
        switch (reason) {
            case SEMAPHORE_MUTEX:
                return "SEMAPHORE_MUTEX (región crítica ocupada)";
            case BUFFER_FULL:
                return "BUFFER_FULL (esperando espacio)";
            case BUFFER_EMPTY:
                return "BUFFER_EMPTY (esperando elementos)";
            case NETWORK_LATENCY:
                return "NETWORK_LATENCY (acceso remoto)";
            case IO_WAIT:
                return "IO_WAIT (operación de E/S)";
            default:
                return reason.name();
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
