/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.kernel;

/**
 * Destino de los eventos del sistema (admisiones, despachos, bloqueos,
 * terminaciones...). El kernel solo "avisa"; quien implemente esta interfaz
 * decide qué hacer: imprimir en consola (pruebas) o mostrar en la GUI (log
 * de eventos). Así el kernel no depende de la GUI.
 *
 * @author SebMarval
 */
public interface EventLogger {

    void log(int cycle, int computerId, String message);

    /** Implementación por defecto: imprime en consola. */
    EventLogger CONSOLE = (cycle, computerId, message) ->
            System.out.println("[ciclo " + cycle + "] [PC" + computerId + "] " + message);

    /** Implementación vacía, para pruebas silenciosas. */
    EventLogger SILENT = (cycle, computerId, message) -> { };
}