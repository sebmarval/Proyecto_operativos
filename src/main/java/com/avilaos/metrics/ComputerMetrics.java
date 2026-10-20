/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.metrics;

/**
 *
 * @author aleja
 */
// producedHosted y consumedHosted suman los buffers que viven en este computador.
public record ComputerMetrics(
        int computerId,
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
        int producedHosted,
        int consumedHosted) {
}