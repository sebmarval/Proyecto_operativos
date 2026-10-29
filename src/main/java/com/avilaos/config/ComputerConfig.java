package com.avilaos.config;

// Datos de un computador en el archivo de configuración.
// policy va como texto (ej. "ROUND_ROBIN") y se convierte al enum al construir el clúster.
public class ComputerConfig {

    public int ramCapacity;
    public String policy;
    public int quantum;

    public ComputerConfig() {
    }

    public ComputerConfig(int ramCapacity, String policy, int quantum) {
        this.ramCapacity = ramCapacity;
        this.policy = policy;
        this.quantum = quantum;
    }
}
