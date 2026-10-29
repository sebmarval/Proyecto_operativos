/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.hardware;

import com.avilaos.datastructures.CustomLinkedList;
import com.avilaos.datastructures.CustomQueue;
import com.avilaos.enums.BlockReason;
import com.avilaos.model.ProcessControlBlock;

/**
 * Dispositivo que atiende solicitudes que duran un número fijo de ciclos:
 * el controlador de E/S (DMA) y la interfaz de red de cada computador.
 *
 * Como el resto del hardware, NO toma decisiones: el kernel le entrega un
 * proceso con startService(), el dispositivo cuenta los ciclos en cada tick
 * del reloj (ClockListener) y deja los procesos atendidos en 'completed'.
 * El kernel los recoge con drainCompleted() y decide qué hacer (pasarlos a READY).
 *
 * Mientras tanto la CPU queda libre para otros procesos: eso es justamente lo
 * que aporta un DMA frente a la E/S programada.
 *
 * Sin sincronización interna: lo usa solo el kernel, dentro de su lock.
 *
 * @author SebMarval
 */
public class IODevice implements ClockListener {

    private final String name;
    private final BlockReason reason;
    private final CustomLinkedList<ProcessControlBlock> inService = new CustomLinkedList<>();
    private final CustomQueue<ProcessControlBlock> completed = new CustomQueue<>();
    private int busyCycles;

    /**
     * @param name   nombre para la GUI y el log ("DMA", "Red")
     * @param reason motivo de bloqueo de los procesos que atiende
     */
    public IODevice(String name, BlockReason reason) {
        this.name = name;
        this.reason = reason;
    }

    /** Recibe un proceso: queda BLOCKED con el motivo del dispositivo durante 'cycles' ciclos. */
    public void startService(ProcessControlBlock process, int cycles) {
        if (cycles <= 0) {
            throw new IllegalArgumentException("La duración del servicio debe ser mayor que 0");
        }
        process.blockFor(reason, cycles);
        inService.add(process);
    }

    /** Un ciclo de trabajo: descuenta 1 a cada solicitud; las que terminan pasan a 'completed'. */
    @Override
    public void onTick(int cycle) {
        if (!inService.isEmpty()) {
            busyCycles++;
        }
        int i = 0;
        while (i < inService.getSize()) {
            ProcessControlBlock p = inService.get(i);
            if (p.tickBlockTimer()) {
                inService.removeAt(i);       // no se avanza i: el siguiente ocupó esta posición
                completed.enqueue(p);
            } else {
                i++;
            }
        }
    }

    /** Entrega (y vacía) los procesos cuyo servicio terminó en el último tick. */
    public ProcessControlBlock[] drainCompleted() {
        ProcessControlBlock[] result = new ProcessControlBlock[completed.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = completed.dequeue();
        }
        return result;
    }

    /** Cancela la solicitud de un proceso que terminó antes (p. ej. venció su deadline). */
    public boolean cancel(ProcessControlBlock process) {
        return inService.remove(process) || completed.remove(process);
    }

    public String getName() { return name; }
    public BlockReason getReason() { return reason; }
    public int getPendingCount() { return inService.getSize(); }
    public int getBusyCycles() { return busyCycles; }
    public Object[] getInServiceSnapshot() { return inService.toArray(); }
}
