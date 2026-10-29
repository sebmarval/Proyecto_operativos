/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.metrics;

/**
 *
 * @author aleja
 */
// Métricas de todo el sistema. La utilización es el promedio de la de cada computador.
public record GlobalMetrics(
        int computerCount,
        int cycles,
        int terminated,
        int completedOnTime,
        int deadlineMissed,
        double throughput,
        double throughputPer100,
        double cpuUtilization,
        double avgResponseTime,
        double deadlineCompliance,
        double fairness,
        double avgSemaphoreBlocked,
        BufferMetrics[] buffers) {
}
