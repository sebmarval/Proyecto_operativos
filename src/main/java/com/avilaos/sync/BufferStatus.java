/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.sync;

/**
 * Fotografía inmutable del estado de un buffer en un instante, para que la GUI
 * la muestre sin tocar el buffer real (que otros hilos están modificando).
 *
 * @param items       elementos actualmente en el buffer, en orden de salida
 * @param waitingS    procesos (PCB) esperando en el mutex s
 * @param waitingN    procesos esperando en n (buffer vacío)
 * @param waitingE    procesos esperando en e (buffer lleno)
 *
 * @author SebMarval
 */
public record BufferStatus(
        String id,
        int hostComputerId,
        int capacity,
        int count,
        int[] items,
        int valueS,
        int valueN,
        int valueE,
        Object[] waitingS,
        Object[] waitingN,
        Object[] waitingE,
        int produced,
        int consumed) {
}
