/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.metrics;

/**
 *
 * @author aleja
 */
// Datos de un buffer: cuánto tiene y cuántos elementos se han producido y consumido.
public record BufferMetrics(
        String id,
        int hostComputerId,
        int count,
        int capacity,
        int produced,
        int consumed) {
}
