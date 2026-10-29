/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.kernel;

import com.avilaos.model.ProcessControlBlock;

/**
 * Entrega un proceso despertado por un semSignal al kernel de SU computador.
 *
 * Es necesario porque un semSignal ejecutado en el computador A puede
 * despertar a un proceso del computador B (buffer compartido). El kernel de A
 * no toca las colas de B: solo deja el aviso en el buzón de B, que B procesa
 * al inicio de su siguiente ciclo. Así ningún hilo toma el lock de otro
 * kernel y no puede haber interbloqueo entre computadores.
 *
 * 'cycle' es el ciclo en que ocurrió el semSignal: el despertar se aplica
 * siempre en un ciclo POSTERIOR, sin importar qué hilo terminó primero
 * (resultado determinista aunque los computadores corran en paralelo).
 *
 * @author SebMarval
 */
public interface WakeUpRouter {

    void deliverWakeUp(ProcessControlBlock process, int cycle);
}
