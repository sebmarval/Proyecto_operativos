/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.hardware;

/**
 * Memoria principal de un computador.
 *
 * Modelo del enunciado: capacidad fija y configurable, sin disco, sin memoria
 * virtual y sin particiones. Solo se lleva la cuenta de unidades usadas y
 * libres. Un proceso pasa de NEW a READY únicamente si allocate() tiene éxito.
 * Los buffers también ocupan RAM en su computador anfitrión.
 *
 * Extensión prevista (Proyecto 2): esta clase es el punto donde se
 * incorporarían marcos de página y memoria virtual.
 *
 * Sin sincronización interna: el kernel la protege con su Semaphore de Java.
 *
 * @author SebMarval
 */
public class RAM {

    private final int capacity;
    private int used;

    public RAM(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("La capacidad de RAM debe ser mayor que 0");
        }
        this.capacity = capacity;
        this.used = 0;
    }

    /** True si hay espacio para 'amount' unidades (no reserva nada). */
    public boolean canAllocate(int amount) {
        return amount > 0 && amount <= getFree();
    }

    /**
     * Reserva 'amount' unidades si hay espacio.
     *
     * @return true si se reservó; false si no alcanza (la RAM no cambia)
     */
    public boolean allocate(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("La cantidad a reservar debe ser mayor que 0");
        }
        if (!canAllocate(amount)) {
            return false;
        }
        used += amount;
        return true;
    }

    /** Libera 'amount' unidades (al terminar un proceso o eliminar un buffer). */
    public void free(int amount) {
        if (amount <= 0 || amount > used) {
            throw new IllegalArgumentException(
                    "Liberación inválida: " + amount + " (en uso: " + used + ")");
        }
        used -= amount;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getUsed() {
        return used;
    }

    public int getFree() {
        return capacity - used;
    }

    /** Porcentaje de uso (0 a 100), útil para la GUI. */
    public double getUsagePercent() {
        return (used * 100.0) / capacity;
    }

    @Override
    public String toString() {
        return "RAM[" + used + "/" + capacity + "]";
    }
}
