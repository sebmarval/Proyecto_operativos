/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.tests;

import com.avilaos.enums.ProcessType;
import com.avilaos.enums.SemaphoreType;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.sync.BoundedBuffer;
import com.avilaos.sync.BufferStatus;
import com.avilaos.sync.SimSemaphore;

/**
 * Prueba manual de los semáforos simulados y del buffer acotado.
 * Ejecutar en NetBeans: clic derecho sobre este archivo -> Run File (Shift+F6).
 *
 * @author SebMarval
 */
public class SyncTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws InterruptedException {
        testSemaphoreBlocksAndWakesFifo();
        testCancelWait();
        testBufferSemaphoresInitialValues();
        testProducerBlocksWhenFull();
        testCircularBuffer();
        testRealThreadsOnSameBuffer();
        System.out.println("--------------------------------");
        System.out.println("Pruebas OK: " + passed + " | Fallidas: " + failed);
    }

    private static void testSemaphoreBlocksAndWakesFifo() {
        SimSemaphore s = new SimSemaphore("s", SemaphoreType.MUTEX, 1);
        ProcessControlBlock a = pcb("A");
        ProcessControlBlock b = pcb("B");
        ProcessControlBlock c = pcb("C");

        check("A entra a la sección crítica sin bloquearse", !s.semWait(a) && s.getValue() == 0);
        check("B se bloquea", s.semWait(b) && s.getValue() == -1);
        check("C se bloquea (|valor| = procesos en cola)", s.semWait(c) && s.getValue() == -2 && s.getWaitingCount() == 2);
        check("semSignal despierta a B primero (FIFO)", s.semSignal() == b && s.getValue() == -1);
        check("luego a C", s.semSignal() == c && s.getValue() == 0);
        check("sin nadie esperando, semSignal no despierta a nadie", s.semSignal() == null && s.getValue() == 1);
    }

    private static void testCancelWait() {
        SimSemaphore n = new SimSemaphore("n", SemaphoreType.FULL, 0);
        ProcessControlBlock dead = pcb("muerto");
        n.semWait(dead);
        check("cancelWait saca al proceso y restaura el valor", n.cancelWait(dead) && n.getValue() == 0);
        check("un semSignal posterior no despierta a un proceso muerto", n.semSignal() == null);
    }

    private static void testBufferSemaphoresInitialValues() {
        BoundedBuffer buf = new BoundedBuffer("B1", 3, 0);
        BufferStatus st = buf.snapshot();
        check("valores iniciales s=1, n=0, e=capacidad", st.valueS() == 1 && st.valueN() == 0 && st.valueE() == 3);
        check("ocupa RAM igual a su capacidad", buf.getMemoryRequired() == 3);
        check("es remoto para otro computador", buf.isRemoteFor(1) && !buf.isRemoteFor(0));
    }

    /** Capacidad 2: el tercer semWait(e) bloquea al productor (motivo BUFFER_FULL). */
    private static void testProducerBlocksWhenFull() {
        BoundedBuffer buf = new BoundedBuffer("B2", 2, 0);
        ProcessControlBlock prod = pcb("productor");
        ProcessControlBlock cons = pcb("consumidor");

        check("consumidor con buffer vacío se bloquea en n", buf.semWait(SemaphoreType.FULL, cons));
        check("motivo de bloqueo en n = BUFFER_EMPTY",
                SemaphoreType.FULL.getBlockReason() == com.avilaos.enums.BlockReason.BUFFER_EMPTY);

        buf.semWait(SemaphoreType.EMPTY, prod);
        buf.add();
        ProcessControlBlock woken = buf.semSignal(SemaphoreType.FULL);
        check("semSignal(n) despierta al consumidor", woken == cons);

        buf.semWait(SemaphoreType.EMPTY, prod);
        buf.add();
        check("tercer semWait(e) bloquea al productor", buf.semWait(SemaphoreType.EMPTY, prod));
        check("add sin semWait(e) previo es un error detectado", throwsISE(buf::add));
    }

    private static void testCircularBuffer() {
        BoundedBuffer buf = new BoundedBuffer("B3", 2, 0);
        int i1 = buf.add();
        int i2 = buf.add();
        int r1 = buf.remove();
        int i3 = buf.add();                   // reutiliza la posición 0 (circular)
        BufferStatus st = buf.snapshot();
        check("orden FIFO de elementos", r1 == i1);
        check("el arreglo circular da la vuelta",
                st.count() == 2 && st.items()[0] == i2 && st.items()[1] == i3);
        check("contadores producidos/consumidos", st.produced() == 3 && st.consumed() == 1);
        buf.remove();
        buf.remove();
        check("remove con buffer vacío es un error detectado", throwsISE(buf::remove));
    }

    /**
     * Dos hilos REALES de Java hacen 20.000 semSignal cada uno sobre el mismo
     * buffer. Sin el lock (Semaphore de Java) se perderían incrementos.
     */
    private static void testRealThreadsOnSameBuffer() throws InterruptedException {
        BoundedBuffer buf = new BoundedBuffer("B4", 1, 0);
        Runnable worker = () -> {
            for (int i = 0; i < 20_000; i++) {
                buf.semSignal(SemaphoreType.FULL);
            }
        };
        Thread t1 = new Thread(worker);
        Thread t2 = new Thread(worker);
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        check("el lock del buffer evita condiciones de carrera (n = 40000)",
                buf.snapshot().valueN() == 40_000);
    }

    // ---------------------------------------------------------------- auxiliares

    private static ProcessControlBlock pcb(String name) {
        return new ProcessControlBlock(name, 0, ProcessType.CPU_BOUND, 1, 1, 100, 10);
    }

    private static boolean throwsISE(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("[OK]    " + name);
        } else {
            failed++;
            System.out.println("[FALLA] " + name);
        }
    }
}

