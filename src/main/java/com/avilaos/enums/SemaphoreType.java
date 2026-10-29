/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.enums;

/**
 * Los tres semáforos de cada buffer acotado (Stallings, Fig. 5.13).
 *
 *  - MUTEX (s): binario, exclusión mutua sobre el buffer. Valor inicial 1.
 *  - FULL  (n): de conteo, cuántos elementos hay para consumir. Valor inicial 0.
 *  - EMPTY (e): de conteo, cuántos espacios libres hay. Valor inicial = capacidad.
 *
 * Cada tipo sabe con qué motivo queda bloqueado un proceso que espera en él.
 *
 * @author SebMarval
 */
public enum SemaphoreType {

    MUTEX("s", BlockReason.SEMAPHORE_MUTEX),
    FULL("n", BlockReason.BUFFER_EMPTY),    // se espera en n cuando el buffer está vacío
    EMPTY("e", BlockReason.BUFFER_FULL);    // se espera en e cuando el buffer está lleno

    private final String symbol;
    private final BlockReason blockReason;

    SemaphoreType(String symbol, BlockReason blockReason) {
        this.symbol = symbol;
        this.blockReason = blockReason;
    }

    public String getSymbol() {
        return symbol;
    }

    public BlockReason getBlockReason() {
        return blockReason;
    }
}
