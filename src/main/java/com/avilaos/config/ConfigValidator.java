package com.avilaos.config;

import com.avilaos.enums.ProcessType;
import com.avilaos.enums.SchedulingPolicy;

// Revisa que la configuración sea válida. Si algo falla lanza IllegalArgumentException
// con un mensaje que dice qué campo está mal y cuál es el rango.
public final class ConfigValidator {

    public static final int MIN_COMPUTERS = 2;
    public static final int MAX_COMPUTERS = 8;

    private ConfigValidator() {
    }

    public static void validate(SimulationConfig c) {
        if (c == null) {
            throw new IllegalArgumentException("La configuración está vacía");
        }
        range("Duración del ciclo (ms)", c.cycleDurationMs, 0, 10000);
        range("Latencia de red (ciclos)", c.networkLatency, 0, 100);

        if (c.computers == null) {
            throw new IllegalArgumentException("Falta la lista de computadores");
        }
        range("Número de computadores", c.computers.length, MIN_COMPUTERS, MAX_COMPUTERS);
        int maxRam = 0;
        for (int i = 0; i < c.computers.length; i++) {
            validateComputer(c.computers[i], i);
            if (c.computers[i].ramCapacity > maxRam) {
                maxRam = c.computers[i].ramCapacity;
            }
        }

        BufferConfig[] buffers = c.buffers == null ? new BufferConfig[0] : c.buffers;
        for (int i = 0; i < buffers.length; i++) {
            validateBuffer(buffers[i], i, c.computers.length, buffers);
        }

        ProcessConfig[] processes = c.processes == null ? new ProcessConfig[0] : c.processes;
        for (int i = 0; i < processes.length; i++) {
            validateProcess(processes[i], i, c, buffers, maxRam);
        }
    }

    private static void validateComputer(ComputerConfig pc, int i) {
        if (pc == null) {
            throw new IllegalArgumentException("Computador " + i + ": datos vacíos");
        }
        range("Computador " + i + " - RAM", pc.ramCapacity, 16, 65536);
        range("Computador " + i + " - Quantum", pc.quantum, 1, 100);
        if (parsePolicy(pc.policy) == null) {
            throw new IllegalArgumentException("Computador " + i + ": política inválida '" + pc.policy
                    + "'. Válidas: " + validPolicies());
        }
    }

    private static void validateBuffer(BufferConfig b, int i, int computerCount, BufferConfig[] all) {
        String label = "Buffer " + (i + 1);
        if (b == null) {
            throw new IllegalArgumentException(label + ": datos vacíos");
        }
        if (b.id == null || b.id.trim().isEmpty()) {
            throw new IllegalArgumentException(label + ": el id no puede estar vacío");
        }
        for (int j = 0; j < i; j++) {
            if (all[j] != null && b.id.equals(all[j].id)) {
                throw new IllegalArgumentException("El id de buffer '" + b.id + "' está repetido");
            }
        }
        range(label + " - capacidad", b.capacity, 1, 100);
        range(label + " - computador anfitrión", b.hostComputerId, 0, computerCount - 1);
    }

    private static void validateProcess(ProcessConfig p, int i, SimulationConfig c,
            BufferConfig[] buffers, int maxRam) {
        String label = "Proceso " + (i + 1);
        if (p == null) {
            throw new IllegalArgumentException(label + ": datos vacíos");
        }
        if (p.name == null || p.name.trim().isEmpty() || p.name.trim().length() > 30) {
            throw new IllegalArgumentException(label + ": el nombre no puede estar vacío ni pasar de 30 caracteres");
        }
        label = "Proceso '" + p.name + "'";
        ProcessType type = parseType(p.type);
        if (type == null) {
            throw new IllegalArgumentException(label + ": tipo inválido '" + p.type + "'");
        }
        range(label + " - computador (-1 = automático)", p.computerId, -1, c.computers.length - 1);
        range(label + " - prioridad", p.priority, 0, 99);
        range(label + " - deadline", p.deadline, 1, 100000);

        // la memoria no puede pasar de la RAM del computador elegido (o del mayor si es automático)
        int ram = p.computerId >= 0 ? c.computers[p.computerId].ramCapacity : maxRam;
        range(label + " - memoria", p.memory, 1, ram);

        switch (type) {
            case CPU_BOUND:
                range(label + " - instrucciones", p.instructions, 1, 10000);
                break;
            case IO_BOUND:
                range(label + " - instrucciones", p.instructions, 1, 10000);
                range(label + " - E/S cada N instrucciones", p.ioEvery, 1, p.instructions);
                range(label + " - duración de la E/S", p.ioDuration, 1, 1000);
                break;
            case PRODUCER:
            case CONSUMER:
                if (p.bufferId == null || !bufferExists(buffers, p.bufferId)) {
                    throw new IllegalArgumentException(label + ": el buffer '" + p.bufferId + "' no existe");
                }
                range(label + " - elementos", p.items, 1, 1000);
                range(label + " - ciclos de trabajo", p.workCycles, 1, 100);
                break;
            default:
                throw new IllegalArgumentException(label + ": tipo no soportado " + type);
        }
    }

    private static boolean bufferExists(BufferConfig[] buffers, String id) {
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] != null && id.equals(buffers[i].id)) {
                return true;
            }
        }
        return false;
    }

    private static void range(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " debe estar entre " + min + " y " + max
                    + " (recibido: " + value + ")");
        }
    }

    // Devuelven null si el texto no corresponde a ningún valor del enum
    public static SchedulingPolicy parsePolicy(String text) {
        if (text == null) {
            return null;
        }
        try {
            return SchedulingPolicy.valueOf(text.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static ProcessType parseType(String text) {
        if (text == null) {
            return null;
        }
        try {
            return ProcessType.valueOf(text.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String validPolicies() {
        SchedulingPolicy[] values = SchedulingPolicy.values();
        String result = "";
        for (int i = 0; i < values.length; i++) {
            result += (i == 0 ? "" : ", ") + values[i];
        }
        return result;
    }
}
