package com.avilaos.gui;

import com.avilaos.model.ProcessControlBlock;
import com.avilaos.sync.BoundedBuffer;
import com.avilaos.sync.BufferStatus;
import com.avilaos.system.Cluster;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

// Parte global de la ventana: control del reloj, velocidad, alta de procesos y buffers,
// tabla de buffers y log de eventos.
//
// Solo ENVÍA órdenes al Cluster (start/pause/resume/stop, setCycleDurationMs, submit,
// createBuffer) y LEE copias seguras (getBuffersSnapshot -> BoundedBuffer.snapshot()).
// No decide nada del sistema.
public class GlobalPanel extends JPanel {

    // GlobalClock no se puede reiniciar: tras stop() sus hilos mueren (ver ClockWorker.stop).
    // Por eso DETENIDA es un estado final.
    private enum ClockState { SIN_INICIAR, EN_EJECUCION, EN_PAUSA, DETENIDA }

    private static final String[] BUFFER_COLUMNS = {"Buffer", "Anfitrión", "Elementos",
        "s (mutex)", "n (elementos)", "e (espacios)",
        "Esperan en s", "Esperan en n", "Esperan en e", "Producidos", "Consumidos"};

    private final Cluster cluster;
    private ClockState state = ClockState.SIN_INICIAR;

    private final JLabel cycleLabel = new JLabel("Ciclo actual: 0");
    private final JLabel stateLabel = new JLabel();
    private final JLabel latencyLabel = new JLabel();
    private final JTextField speedField = new JTextField(5);
    private final JButton startButton = new JButton("Iniciar");
    private final JButton pauseButton = new JButton("Pausar");
    private final JButton resumeButton = new JButton("Reanudar");
    private final JButton stopButton = new JButton("Finalizar simulación");
    private final JButton processButton = new JButton("Crear proceso...");
    private final JButton bufferButton = new JButton("Crear buffer...");
    private final JButton randomOneButton = new JButton("Proceso aleatorio");
    private final JButton randomFiveButton = new JButton("5 aleatorios");

    private final DefaultTableModel buffersModel = new DefaultTableModel(new Object[0][0], BUFFER_COLUMNS) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    public GlobalPanel(Cluster cluster, JTextArea logArea) {
        this.cluster = cluster;
        speedField.setText(String.valueOf(cluster.getClock().getCycleDurationMs()));

        startButton.addActionListener(e -> {
            cluster.start();
            setState(ClockState.EN_EJECUCION);
        });
        pauseButton.addActionListener(e -> {
            cluster.pause();
            setState(ClockState.EN_PAUSA);
        });
        resumeButton.addActionListener(e -> {
            cluster.resume();
            setState(ClockState.EN_EJECUCION);
        });
        stopButton.addActionListener(e -> confirmStop());
        JButton speedButton = new JButton("Aplicar");
        speedButton.addActionListener(e -> applySpeed());
        processButton.addActionListener(e ->
                new ProcessDialog(SwingUtilities.getWindowAncestor(this), cluster).setVisible(true));
        bufferButton.addActionListener(e ->
                new BufferDialog(SwingUtilities.getWindowAncestor(this), cluster).setVisible(true));
        randomOneButton.addActionListener(e -> addRandomProcesses(1));
        randomFiveButton.addActionListener(e -> addRandomProcesses(5));

        JPanel clockRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        clockRow.add(cycleLabel);
        clockRow.add(stateLabel);
        clockRow.add(latencyLabel);
        clockRow.add(startButton);
        clockRow.add(pauseButton);
        clockRow.add(resumeButton);
        clockRow.add(stopButton);
        clockRow.add(new JLabel("  Duración del ciclo (ms):"));
        clockRow.add(speedField);
        clockRow.add(speedButton);

        JPanel createRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        createRow.add(processButton);
        createRow.add(bufferButton);
        createRow.add(new JLabel("  Pruebas:"));
        createRow.add(randomOneButton);
        createRow.add(randomFiveButton);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.add(clockRow);
        controls.add(createRow);

        JTable buffersTable = new JTable(buffersModel);
        buffersTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        buffersTable.setFillsViewportHeight(true);
        JScrollPane buffersScroll = new JScrollPane(buffersTable);
        buffersScroll.setBorder(BorderFactory.createTitledBorder("Buffers (semáforos y procesos en espera)"));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log de eventos"));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buffersScroll, logScroll);
        split.setResizeWeight(0.55);

        setLayout(new BorderLayout());
        add(controls, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        setState(ClockState.SIN_INICIAR);
    }

    // ---------------------------------------------------------------- órdenes

    private void confirmStop() {
        int answer = JOptionPane.showConfirmDialog(this,
                "Finalizar es definitivo: el reloj no se puede reiniciar en esta ventana.\n"
                + "Los resultados quedan visibles. Para otra simulación hay que  "
                + "volver a la configuración. ¿Finalizar?",
                "Finalizar simulación", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            cluster.stop();
            setState(ClockState.DETENIDA);
        }
    }

    // La duración se puede cambiar con el reloj corriendo (GlobalClock.cycleDurationMs es volatile)
    private void applySpeed() {
        int ms;
        try {
            ms = Integer.parseInt(speedField.getText().trim());
        } catch (NumberFormatException e) {
            error("La duración del ciclo debe ser un número entero entre 0 y 10000");
            return;
        }
        if (ms < 0 || ms > 10000) {
            error("La duración del ciclo debe estar entre 0 y 10000 ms (recibido: " + ms + ")");
            return;
        }
        try {
            cluster.setCycleDurationMs(ms);
        } catch (IllegalArgumentException ex) {
            error(ex.getMessage());
        }
    }

    // Solo para pruebas: manda procesos con valores al azar usando la misma orden submit
    private void addRandomProcesses(int count) {
        for (int i = 0; i < count; i++) {
            try {
                cluster.submit(RandomProcessFactory.create(cluster));
            } catch (IllegalArgumentException ex) {
                error(ex.getMessage());
                return;
            }
        }
    }

    private void setState(ClockState newState) {
        state = newState;
        startButton.setEnabled(state == ClockState.SIN_INICIAR);
        pauseButton.setEnabled(state == ClockState.EN_EJECUCION);
        resumeButton.setEnabled(state == ClockState.EN_PAUSA);
        stopButton.setEnabled(state == ClockState.EN_EJECUCION || state == ClockState.EN_PAUSA);
        boolean canCreate = state != ClockState.DETENIDA;
        processButton.setEnabled(canCreate);
        bufferButton.setEnabled(canCreate);
        randomOneButton.setEnabled(canCreate);
        randomFiveButton.setEnabled(canCreate);
        stateLabel.setText("  Estado: " + state.name().replace('_', ' ').toLowerCase() + "  ");
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(this, message, "Dato inválido", JOptionPane.ERROR_MESSAGE);
    }

    // ---------------------------------------------------------------- refresco (Timer de MainFrame)

    public void refresh() {
        cycleLabel.setText("Ciclo actual: " + cluster.getCurrentCycle());
        latencyLabel.setText("  Latencia de red: " + cluster.getNetworkLatency() + " ciclos  ");

        // snapshot() entrega una copia inmutable del buffer; aquí no se toca el buffer real
        Object[] raw = cluster.getBuffersSnapshot();
        buffersModel.setRowCount(0);
        for (int i = 0; i < raw.length; i++) {
            BufferStatus st = ((BoundedBuffer) raw[i]).snapshot();
            buffersModel.addRow(new Object[]{st.id(), "PC" + st.hostComputerId(),
                st.count() + " / " + st.capacity(),
                st.valueS(), st.valueN(), st.valueE(),
                names(st.waitingS()), names(st.waitingN()), names(st.waitingE()),
                st.produced(), st.consumed()});
        }
    }

    // "P3 Sensor, P5 Monitor" o "—" si no espera nadie
    private static String names(Object[] waiting) {
        if (waiting.length == 0) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < waiting.length; i++) {
            ProcessControlBlock p = (ProcessControlBlock) waiting[i];
            sb.append(i == 0 ? "" : ", ").append("P").append(p.getId()).append(" ").append(p.getName());
        }
        return sb.toString();
    }
}
