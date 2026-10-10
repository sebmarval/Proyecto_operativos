/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package com.avilaos.enums;

/**
 *
 * @author aleja
 */
public enum BlockReason {
    NONE,
    SEMAPHORE_MUTEX,
    BUFFER_FULL,
    BUFFER_EMPTY,
    NETWORK_LATENCY,
    IO_WAIT
    /*TODO: Revisar si se agregara un motivo 
    bloqueo de proceso en cola adicional es esos (@author SebMarval).*/
}
