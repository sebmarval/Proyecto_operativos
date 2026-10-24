package com.avilaos.gui;

import com.avilaos.config.BufferConfig;
import com.avilaos.config.ClusterFactory;
import com.avilaos.config.ComputerConfig;
import com.avilaos.config.ConfigLoader;
import com.avilaos.config.ConfigValidator;
import com.avilaos.config.ProcessConfig;
import com.avilaos.config.SimulationConfig;
import com.avilaos.enums.ProcessType;
import com.avilaos.enums.SchedulingPolicy;
import com.avilaos.system.Cluster;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

// Ventana de configuración inicial. Aquí no hay lógica del SO: solo arma una
// SimulationConfig con lo que escribe el usuario, la valida y construye el clúster.
public class ConfigFrame extends JFrame {

    private static final String[] COMPUTER_COLS = {"Computador", "RAM", "Política", "Quantum"};
    private static final String[] BUFFER_COLS = {"Id", "Capacidad", "Computador anfitrión"};
    private static final String[] PROCESS_COLS = {"Nombre", "Tipo", "Computador (-1 = auto)", "Prioridad",
        "Memoria", "Deadline", "Instrucciones", "E/S cada N", "E/S dura M", "Buffer", "Elementos", "Ciclos de trabajo"};

    // opciones de RAM (potencias de 2) para la lista desplegable
    private static final String[] RAM_OPTIONS = {"16", "32", "64", "128", "256", "512", "1024"};

    private final JTextField cycleField = new JTextField("500", 6);
    private final JTextField latencyField = new JTextField("2", 6);

    private final DefaultTableModel computersModel = newModel(COMPUTER_COLS, true);
    private final DefaultTableModel buffersModel = newModel(BUFFER_COLS, false);
    private final DefaultTableModel processesModel = newModel(PROCESS_COLS, false);
    private final JTable computersTable = new JTable(computersModel);
    private final JTable buffersTable = new JTable(buffersModel);
    private final JTable processesTable = new JTable(processesModel);

    public ConfigFrame() {
        super("ÁvilaOS - Configuración");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // listas desplegables dentro de las tablas
        computersTable.getColumnModel().getColumn(1)
                .setCellEditor(new DefaultCellEditor(new JComboBox<>(RAM_OPTIONS)));
        computersTable.getColumnModel().getColumn(2)
                .setCellEditor(new DefaultCellEditor(new JComboBox<>(policyNames())));
        processesTable.getColumnModel().getColumn(1)
                .setCellEditor(new DefaultCellEditor(new JComboBox<>(typeNames())));

        // valores iniciales: 2 computadores iguales (los del botón Agregar salen al azar)
        addFixedComputerRow();
        addFixedComputerRow();

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Duración del ciclo (ms):"));
        top.add(cycleField);
        top.add(new JLabel("Latencia de red (ciclos):"));
        top.add(latencyField);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Computadores", buildTab(computersTable, computersModel, this::addComputerRow));
        tabs.addTab("Buffers", buildTab(buffersTable, buffersModel, this::addBufferRow));
        tabs.addTab("Procesos iniciales", buildTab(processesTable, processesModel, this::addProcessRow));

        JButton loadButton = new JButton("Cargar JSON");
        JButton saveButton = new JButton("Guardar JSON");
        JButton startButton = new JButton("Iniciar");
        loadButton.addActionListener(e -> loadFromFile());
        saveButton.addActionListener(e -> saveToFile());
        startButton.addActionListener(e -> startSimulation());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(loadButton);
        bottom.add(saveButton);
        bottom.add(startButton);

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setSize(950, 520);
        setLocationRelativeTo(null);
    }

    // ---------------------------------------------------------------- botones

    private void loadFromFile() {
        JFileChooser chooser = new JFileChooser(new File("config"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            fillTables(ConfigLoader.load(chooser.getSelectedFile()));
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void saveToFile() {
        try {
            SimulationConfig cfg = readConfig();
            ConfigValidator.validate(cfg);
            JFileChooser chooser = new JFileChooser(new File("config"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File file = chooser.getSelectedFile();
            if (!file.getName().endsWith(".json")) {
                file = new File(file.getPath() + ".json");
            }
            ConfigLoader.save(cfg, file);
            JOptionPane.showMessageDialog(this, "Configuración guardada en " + file.getName());
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    // Construye el clúster con la configuración y abre la ventana principal
    private void startSimulation() {
        try {
            SimulationConfig cfg = readConfig();
            // el log de eventos va a un área de texto que mostrará GlobalPanel
            JTextArea logArea = new JTextArea();
            logArea.setEditable(false);
            Cluster cluster = ClusterFactory.build(cfg, new GuiEventLogger(logArea));
            new MainFrame(cluster, logArea).setVisible(true);
            dispose();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    // ---------------------------------------------------------------- tablas <-> configuración

    private void fillTables(SimulationConfig cfg) {
        cycleField.setText(String.valueOf(cfg.cycleDurationMs));
        latencyField.setText(String.valueOf(cfg.networkLatency));

        computersModel.setRowCount(0);
        if (cfg.computers != null) {
            for (int i = 0; i < cfg.computers.length; i++) {
                ComputerConfig c = cfg.computers[i];
                computersModel.addRow(new Object[]{i, c.ramCapacity, c.policy, c.quantum});
            }
        }
        buffersModel.setRowCount(0);
        if (cfg.buffers != null) {
            for (int i = 0; i < cfg.buffers.length; i++) {
                BufferConfig b = cfg.buffers[i];
                buffersModel.addRow(new Object[]{b.id, b.capacity, b.hostComputerId});
            }
        }
        processesModel.setRowCount(0);
        if (cfg.processes != null) {
            for (int i = 0; i < cfg.processes.length; i++) {
                ProcessConfig p = cfg.processes[i];
                processesModel.addRow(new Object[]{p.name, p.type, p.computerId, p.priority, p.memory,
                    p.deadline, blankIfZero(p.instructions), blankIfZero(p.ioEvery), blankIfZero(p.ioDuration),
                    p.bufferId == null ? "" : p.bufferId, blankIfZero(p.items), blankIfZero(p.workCycles)});
            }
        }
    }

    // Lee lo que hay en pantalla. Si un campo no es número lanza IllegalArgumentException
    // con el campo y la fila; los rangos los revisa ConfigValidator.
    private SimulationConfig readConfig() {
        stopEditing(computersTable);
        stopEditing(buffersTable);
        stopEditing(processesTable);

        SimulationConfig cfg = new SimulationConfig();
        cfg.cycleDurationMs = parseNumber(cycleField.getText(), "Duración del ciclo (ms)");
        cfg.networkLatency = parseNumber(latencyField.getText(), "Latencia de red (ciclos)");

        int n = computersModel.getRowCount();
        cfg.computers = new ComputerConfig[n];
        for (int i = 0; i < n; i++) {
            cfg.computers[i] = new ComputerConfig(
                    readInt(computersModel, i, 1, "Computadores - RAM", false),
                    text(computersModel, i, 2),
                    readInt(computersModel, i, 3, "Computadores - Quantum", false));
        }

        int nb = buffersModel.getRowCount();
        cfg.buffers = new BufferConfig[nb];
        for (int i = 0; i < nb; i++) {
            cfg.buffers[i] = new BufferConfig(
                    text(buffersModel, i, 0),
                    readInt(buffersModel, i, 1, "Buffers - Capacidad", false),
                    readInt(buffersModel, i, 2, "Buffers - Computador anfitrión", false));
        }

        int np = processesModel.getRowCount();
        cfg.processes = new ProcessConfig[np];
        for (int i = 0; i < np; i++) {
            ProcessConfig p = new ProcessConfig();
            p.name = text(processesModel, i, 0);
            p.type = text(processesModel, i, 1);
            p.computerId = readInt(processesModel, i, 2, "Procesos - Computador", false);
            p.priority = readInt(processesModel, i, 3, "Procesos - Prioridad", false);
            p.memory = readInt(processesModel, i, 4, "Procesos - Memoria", false);
            p.deadline = readInt(processesModel, i, 5, "Procesos - Deadline", false);
            // estos campos solo aplican a ciertos tipos, por eso pueden ir vacíos
            p.instructions = readInt(processesModel, i, 6, "Procesos - Instrucciones", true);
            p.ioEvery = readInt(processesModel, i, 7, "Procesos - E/S cada N", true);
            p.ioDuration = readInt(processesModel, i, 8, "Procesos - E/S dura M", true);
            String buffer = text(processesModel, i, 9);
            p.bufferId = buffer.isEmpty() ? null : buffer;
            p.items = readInt(processesModel, i, 10, "Procesos - Elementos", true);
            p.workCycles = readInt(processesModel, i, 11, "Procesos - Ciclos de trabajo", true);
            cfg.processes[i] = p;
        }
        return cfg;
    }

    // ---------------------------------------------------------------- filas nuevas

    // Las filas que agrega el botón salen con valores al azar, para armar pruebas rápido.
    // Se usa Math.random() porque java.util.Random no está permitido en el proyecto.
    private static int rnd(int min, int max) {
        return min + (int) (Math.random() * (max - min + 1));
    }

    private void addFixedComputerRow() {
        computersModel.addRow(new Object[]{computersModel.getRowCount(), 256, SchedulingPolicy.ROUND_ROBIN.name(), 3});
    }

    private void addComputerRow() {
        SchedulingPolicy[] policies = SchedulingPolicy.values();
        computersModel.addRow(new Object[]{computersModel.getRowCount(),
            RAM_OPTIONS[rnd(0, RAM_OPTIONS.length - 1)],
            policies[rnd(0, policies.length - 1)].name(),
            rnd(1, 8)});
    }

    private void addBufferRow() {
        int hosts = Math.max(1, computersModel.getRowCount());
        buffersModel.addRow(new Object[]{"B" + (buffersModel.getRowCount() + 1), rnd(2, 8), rnd(0, hosts - 1)});
    }

    private void addProcessRow() {
        ProcessType[] types = ProcessType.values();
        ProcessType type = types[rnd(0, types.length - 1)];
        int buffers = buffersModel.getRowCount();
        if ((type == ProcessType.PRODUCER || type == ProcessType.CONSUMER) && buffers == 0) {
            type = ProcessType.CPU_BOUND;   // no hay buffer al que conectarlo
        }
        int computers = Math.max(1, computersModel.getRowCount());

        // solo se llenan las columnas que usa el tipo; las demás quedan vacías
        Object instructions = "";
        Object ioEvery = "";
        Object ioDuration = "";
        Object buffer = "";
        Object items = "";
        Object workCycles = "";
        switch (type) {
            case CPU_BOUND:
                instructions = rnd(10, 40);
                break;
            case IO_BOUND:
                instructions = rnd(10, 40);
                ioEvery = rnd(2, 5);
                ioDuration = rnd(2, 6);
                break;
            default:   // PRODUCER y CONSUMER
                buffer = text(buffersModel, rnd(0, buffers - 1), 0);
                items = rnd(3, 8);
                workCycles = rnd(1, 3);
        }
        processesModel.addRow(new Object[]{"P" + (processesModel.getRowCount() + 1), type.name(),
            rnd(-1, computers - 1), rnd(0, 9), rnd(4, 16), rnd(100, 500),
            instructions, ioEvery, ioDuration, buffer, items, workCycles});
    }

    // ---------------------------------------------------------------- auxiliares

    // Panel con la tabla y los botones para agregar y quitar filas
    private JPanel buildTab(JTable table, DefaultTableModel model, Runnable addAction) {
        JButton add = new JButton("Agregar fila");
        JButton remove = new JButton("Quitar fila seleccionada");
        add.addActionListener(e -> addAction.run());
        remove.addActionListener(e -> {
            stopEditing(table);
            int row = table.getSelectedRow();
            if (row < 0) {
                showError("Selecciona primero la fila que quieres quitar");
                return;
            }
            model.removeRow(row);
            if (model == computersModel) {
                // los computadores se numeran por posición
                for (int i = 0; i < model.getRowCount(); i++) {
                    model.setValueAt(i, i, 0);
                }
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(add);
        buttons.add(remove);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private static DefaultTableModel newModel(String[] columns, boolean firstColumnReadOnly) {
        return new DefaultTableModel(new Object[0][0], columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return !(firstColumnReadOnly && column == 0);
            }
        };
    }

    private static void stopEditing(JTable table) {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
    }

    private static String text(DefaultTableModel model, int row, int col) {
        Object value = model.getValueAt(row, col);
        return value == null ? "" : value.toString().trim();
    }

    private static int readInt(DefaultTableModel model, int row, int col, String label, boolean optional) {
        String s = text(model, row, col);
        if (s.isEmpty()) {
            if (optional) {
                return 0;
            }
            throw new IllegalArgumentException("Fila " + (row + 1) + " - " + label + ": no puede estar vacío");
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Fila " + (row + 1) + " - " + label
                    + ": debe ser un número entero (recibido: '" + s + "')");
        }
    }

    private static int parseNumber(String s, String label) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " debe ser un número entero (recibido: '" + s.trim() + "')");
        }
    }

    private static Object blankIfZero(int value) {
        return value == 0 ? "" : (Object) value;
    }

    private static String[] policyNames() {
        SchedulingPolicy[] values = SchedulingPolicy.values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }

    private static String[] typeNames() {
        ProcessType[] values = ProcessType.values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error de configuración", JOptionPane.ERROR_MESSAGE);
    }
}
