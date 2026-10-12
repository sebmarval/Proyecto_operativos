/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.avilaos.hardware;

/**
 * Componente que reacciona a cada tick del reloj global.
 *
 * Lo implementa cada Computer: en cada tick ejecuta exactamente UN ciclo
 * (ver docs/ARQUITECTURA.md, secciones 3 y 4). Usar una interfaz permite que
 * el reloj avance cualquier componente sin conocer su clase concreta.
 *
 * @author SebMarval
 */
public interface ClockListener {

    /**
     * Ejecuta un ciclo completo del componente.
     *
     * @param cycle número del ciclo global actual (empieza en 1)
     */
    void onTick(int cycle);
}
