/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.metrics;

/**
 *
 * @author aleja
 */
public final class MetricsCalculator {
    // Funciones de cálculo de métricas. No guardan nada, 
    //solo reciben números y devuelven el resultado.
    private MetricsCalculator() {
        
    }
    
    public static double jainIndex(double[] x) {
        if (x == null || x.length == 0) {
            return Double.NaN;
        }
        double sum = 0.0;
        double sumSquares = 0.0;
        for (int i = 0; i < x.length; i++) {
            sum += x[i];
            sumSquares += x[i] * x[i];
        }
        if (sumSquares == 0.0) {
            return 1.0;
        }
        return (sum * sum) / (x.length * sumSquares);
    }
 
    /** Promedio de enteros. Arreglo vacío -> NaN. */
    public static double average(int[] values) {
        if (values == null || values.length == 0) {
            return Double.NaN;
        }
        long total = 0;
        for (int i = 0; i < values.length; i++) {
            total += values[i];
        }
        return (double) total / values.length;
    }
 
    /** part / total × 100. Nunca divide entre 0. */
    public static double percentage(int part, int total) {
        return total <= 0 ? 0.0 : (part * 100.0) / total;
    }
 
    /** Procesos completados por ciclo. */
    public static double throughput(int completed, int cycles) {
        return cycles <= 0 ? 0.0 : (double) completed / cycles;
    }
}
