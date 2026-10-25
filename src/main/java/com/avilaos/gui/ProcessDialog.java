package com.avilaos.gui;

import com.avilaos.enums.ProcessType;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.sync.BoundedBuffer;
import com.avilaos.system.Cluster;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

// "Vista 1" del enunciado: crea un proceso en tiempo de ejecución.
// Los campos cambian según el tipo (E/S, productor/consumidor).
// Valida tipo de dato y rango (tabla 5.3 de la guía) ANTES de crear nada: cualquier error
// sale como IllegalArgumentException y se muestra en un cuadro, sin crear el proceso.
// La única orden que manda al sistema es cluster.submit(...).
public class ProcessDialog extends JDialog {

    private final Cluster cluster;
    private final BoundedBuffer[] buffers;

    private final JTextField nameField = new JTextField("Proceso");
    private final JComboBox<ProcessType> typeBox = new JComboBox<>(ProcessType.values());
    private final JComboBox<String> computerBox = new JComboBox<>();
    private final JTextField priorityField = new JTextField("1");
    private final JTextField memoryField = new JTextField("16");
    private final JTextField deadlineField = new JTextField("200");
    private final JTextField instructionsField = new JTextField("20");
    private final JTextField ioEveryField = new JTextField("3");
    private final JTextField ioDurationField = new JTextField("4");
    private final JComboBox<String> bufferBox = new JComboBox<>();
    private final JTextField itemsField = new JTextField("5");
    private final JTextField workField = new JTextField("2");

    // filas que aparecen o se ocultan según el tipo
    private final JPanel instructionsRow;
    private final JPanel ioEveryRow;
    private final JPanel ioDurationRow;
    private final JPanel bufferRow;
    private final JPanel itemsRow;
    private final JPanel workRow;

    public ProcessDialog(Window owner, Cluster cluster) {
        super(owner, "Crear proceso", ModalityType.APPLICATION_MODAL);
        this.cluster = cluster;

        computerBox.addItem("Automático");
        for (int i = 0; i < cluster.getComputerCount(); i++) {
            computerBox.addItem("Computador " + i);
        }
        Object[] raw = cluster.getBuffersSnapshot();
        buffers = new BoundedBuffer[raw.length];
        for (int i = 0; i < raw.length; i++) {
            buffers[i] = (BoundedBuffer) raw[i];
            bufferBox.addItem(buffers[i].getId() + " (en PC" + buffers[i].getHostComputerId() + ")");
        }

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        form.add(row("Nombre:", nameField));
        form.add(row("Tipo:", typeBox));
        form.add(row("Computador:", computerBox));
        form.add(row("Prioridad (0-99, menor = más prioridad):", priorityField));
        form.add(row("Memoria:", memoryField));
        form.add(row("Deadline (ciclos):", deadlineField));
        instructionsRow = row("Instrucciones (1-10000):", instructionsField);
        ioEveryRow = row("E/S cada N instrucciones:", ioEveryField);
        ioDurationRow = row("E/S dura M ciclos (1-1000):", ioDurationField);
        bufferRow = row("Buffer:", bufferBox);
        itemsRow = row("Elementos a producir/consumir (1-1000):", itemsField);
        workRow = row("Ciclos de trabajo por elemento (1-100):", workField);
        form.add(instructionsRow);
        form.add(ioEveryRow);
        form.add(ioDurationRow);
        form.add(bufferRow);
        form.add(itemsRow);
        form.add(workRow);

        JButton create = new JButton("Crear");
        JButton cancel = new JButton("Cancelar");
        create.addActionListener(e -> create());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(create);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        typeBox.addActionListener(e -> updateVisibleRows());
        updateVisibleRows();
        setLocationRelativeTo(owner);
    }

    private static JPanel row(String label, JComponent field) {
        JPanel panel = new JPanel(new GridLayout(1, 2, 8, 0));
        panel.add(new JLabel(label));
        panel.add(field);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height + 6));
        return panel;
    }

    // BoxLayout no reserva espacio para componentes ocultos, por eso se usa setVisible
    private void updateVisibleRows() {
        ProcessType type = (ProcessType) typeBox.getSelectedItem();
        boolean cpuOrIo = type == ProcessType.CPU_BOUND || type == ProcessType.IO_BOUND;
        boolean sync = type == ProcessType.PRODUCER || type == ProcessType.CONSUMER;
        instructionsRow.setVisible(cpuOrIo);
        ioEveryRow.setVisible(type == ProcessType.IO_BOUND);
        ioDurationRow.setVisible(type == ProcessType.IO_BOUND);
        bufferRow.setVisible(sync);
        itemsRow.setVisible(sync);
        workRow.setVisible(sync);
        pack();
    }

    private void create() {
        try {
            String name = nameField.getText().trim();
            if (name.isEmpty() || name.length() > 30) {
                throw new IllegalArgumentException("El nombre no puede estar vacío ni pasar de 30 caracteres");
            }
            ProcessType type = (ProcessType) typeBox.getSelectedItem();

            // la memoria no puede pasar de la RAM del computador elegido (del mayor si es automático)
            int selected = computerBox.getSelectedIndex();
            int ramLimit = 0;
            for (int i = 0; i < cluster.getComputerCount(); i++) {
                int ram = cluster.getComputer(i).getKernel().getRam().getCapacity();
                if (selected == 0 ? ram > ramLimit : i == selected - 1) {
                    ramLimit = ram;
                }
            }
            int priority = readInt(priorityField, "Prioridad", 0, 99);
            int memory = readInt(memoryField, "Memoria", 1, ramLimit);
            int deadline = readInt(deadlineField, "Deadline", 1, 100000);
            int computer = selected == 0 ? cluster.pickComputerAutomatically(memory) : selected - 1;

            ProcessControlBlock process;
            if (type == ProcessType.CPU_BOUND) {
                int instructions = readInt(instructionsField, "Instrucciones", 1, 10000);
                process = new ProcessControlBlock(name, computer, type, priority, memory, deadline, instructions);
            } else if (type == ProcessType.IO_BOUND) {
                int instructions = readInt(instructionsField, "Instrucciones", 1, 10000);
                int every = readInt(ioEveryField, "E/S cada N", 1, instructions);
                int duration = readInt(ioDurationField, "E/S dura M", 1, 1000);
                process = new ProcessControlBlock(name, computer, type, priority, memory, deadline, instructions);
                process.configureIO(every, duration);
            } else {
                if (buffers.length == 0) {
                    throw new IllegalArgumentException("No hay buffers: crea uno primero");
                }
                BoundedBuffer buffer = buffers[bufferBox.getSelectedIndex()];
                int items = readInt(itemsField, "Elementos", 1, 1000);
                int work = readInt(workField, "Ciclos de trabajo", 1, 100);
                process = type == ProcessType.PRODUCER
                        ? ProcessControlBlock.createProducer(name, computer, priority, memory, deadline, buffer, items, work)
                        : ProcessControlBlock.createConsumer(name, computer, priority, memory, deadline, buffer, items, work);
            }
            cluster.submit(process);
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Dato inválido", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static int readInt(JTextField field, String label, int min, int max) {
        String text = field.getText().trim();
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " debe ser un número entero (recibido: '" + text + "')");
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + " debe estar entre " + min + " y " + max
                    + " (recibido: " + value + ")");
        }
        return value;
    }
}
