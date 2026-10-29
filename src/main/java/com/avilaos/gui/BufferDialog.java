package com.avilaos.gui;

import com.avilaos.system.Cluster;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

// Crea un buffer en tiempo de ejecución. Valida id (no vacío) y capacidad (1-100) aquí;
// que el id sea único y que quepa en la RAM del anfitrión lo valida Cluster.createBuffer,
// que lanza IllegalArgumentException y se muestra tal cual.
public class BufferDialog extends JDialog {

    private final Cluster cluster;
    private final JTextField idField = new JTextField();
    private final JTextField capacityField = new JTextField("5");
    private final JComboBox<String> hostBox = new JComboBox<>();

    public BufferDialog(Window owner, Cluster cluster) {
        super(owner, "Crear buffer", ModalityType.APPLICATION_MODAL);
        this.cluster = cluster;
        idField.setText("B" + (cluster.getBuffersSnapshot().length + 1));
        for (int i = 0; i < cluster.getComputerCount(); i++) {
            hostBox.addItem("Computador " + i);
        }

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        form.add(new JLabel("Id:"));
        form.add(idField);
        form.add(new JLabel("Capacidad (1-100):"));
        form.add(capacityField);
        form.add(new JLabel("Computador anfitrión:"));
        form.add(hostBox);

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
        pack();
        setLocationRelativeTo(owner);
    }

    private void create() {
        try {
            String id = idField.getText().trim();
            if (id.isEmpty() || id.length() > 30) {
                throw new IllegalArgumentException("El id no puede estar vacío ni pasar de 30 caracteres");
            }
            int capacity;
            try {
                capacity = Integer.parseInt(capacityField.getText().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("La capacidad debe ser un número entero (recibido: '"
                        + capacityField.getText().trim() + "')");
            }
            if (capacity < 1 || capacity > 100) {
                throw new IllegalArgumentException("La capacidad debe estar entre 1 y 100 (recibido: " + capacity + ")");
            }
            cluster.createBuffer(id, capacity, hostBox.getSelectedIndex());
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "No se pudo crear el buffer",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
