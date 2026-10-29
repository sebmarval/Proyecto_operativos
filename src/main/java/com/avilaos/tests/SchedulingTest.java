package com.avilaos.tests;

import com.avilaos.datastructures.CustomLinkedList;
import com.avilaos.enums.ProcessState;
import com.avilaos.enums.ProcessType;
import com.avilaos.enums.SchedulingPolicy;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.scheduling.SchedulerFactory;
import com.avilaos.scheduling.SchedulingAlgorithm;

// Prueba de las 4 políticas de planificación con los mismos 3 procesos.
// Antes pedía la política por teclado; ahora corre las 4 solas y comprueba el resultado.
// VERBOSE = true imprime el recorrido ciclo por ciclo como la versión interactiva.
public class SchedulingTest {

    private static final boolean VERBOSE = true;
    private static final int QUANTUM = 2;
    private static final int MAX_CYCLES = 100;   // freno por si una política dejara el ciclo sin terminar

    private static int passed = 0;
    private static int failed = 0;

    // finishOrder: nombres separados por coma, en el orden en que terminaron
    private record Result(String finishOrder, int finished, int cycles, int preemptions) {
    }

    public static void main(String[] args) {
        Result fcfs = run(SchedulingPolicy.FCFS);
        Result rr = run(SchedulingPolicy.ROUND_ROBIN);
        Result edf = run(SchedulingPolicy.EDF);
        Result priority = run(SchedulingPolicy.PREEMPTIVE_PRIORITY);

        // Trabajo total: 5 + 3 + 4 = 12 instrucciones, y la CPU nunca queda libre,
        // así que toda política debe terminar en 12 ciclos con los 3 procesos.
        check("FCFS: terminan los 3 en 12 ciclos", fcfs.finished() == 3 && fcfs.cycles() == 12);
        check("FCFS: orden de llegada (P1, P2, P3)", fcfs.finishOrder().equals("P1-Largo,P2-Urgente,P3-Medio"));
        check("FCFS: no expropia", fcfs.preemptions() == 0);

        check("Round Robin: terminan los 3 en 12 ciclos", rr.finished() == 3 && rr.cycles() == 12);
        check("Round Robin: expropia al agotarse el quantum", rr.preemptions() > 0);
        check("Round Robin: los cortos terminan antes que el largo (P2, P3, P1)",
                rr.finishOrder().equals("P2-Urgente,P3-Medio,P1-Largo"));
        check("Round Robin: el orden de término difiere de FCFS", !rr.finishOrder().equals(fcfs.finishOrder()));

        check("EDF: terminan los 3 en 12 ciclos", edf.finished() == 3 && edf.cycles() == 12);
        check("EDF: primero el deadline más cercano (P2=8, P3=15, P1=30)",
                edf.finishOrder().equals("P2-Urgente,P3-Medio,P1-Largo"));

        check("Prioridades: terminan los 3 en 12 ciclos", priority.finished() == 3 && priority.cycles() == 12);
        check("Prioridades: primero la mayor prioridad (P2=1, P3=2, P1=3)",
                priority.finishOrder().equals("P2-Urgente,P3-Medio,P1-Largo"));

        System.out.println("--------------------------------");
        System.out.println("Pruebas OK: " + passed + " | Fallidas: " + failed);
    }

    // Simula una CPU con la política indicada: mismo recorrido que la versión interactiva original
    private static Result run(SchedulingPolicy policy) {
        SchedulingAlgorithm scheduler = SchedulerFactory.create(policy, QUANTUM);
        CustomLinkedList<ProcessControlBlock> readyQueue = new CustomLinkedList<>();
        readyQueue.add(new ProcessControlBlock("P1-Largo", 0, ProcessType.CPU_BOUND, 3, 128, 30, 5));
        readyQueue.add(new ProcessControlBlock("P2-Urgente", 0, ProcessType.CPU_BOUND, 1, 64, 8, 3));
        readyQueue.add(new ProcessControlBlock("P3-Medio", 0, ProcessType.CPU_BOUND, 2, 256, 15, 4));

        say("\n==== " + policy + (policy == SchedulingPolicy.ROUND_ROBIN ? " (quantum " + QUANTUM + ")" : "") + " ====");

        StringBuilder order = new StringBuilder();
        ProcessControlBlock running = null;
        int quantumUsed = 0;
        int cycle = 0;
        int finished = 0;
        int preemptions = 0;

        while ((running != null || !readyQueue.isEmpty()) && cycle < MAX_CYCLES) {
            cycle++;
            say("[ciclo " + cycle + "]");

            if (running == null) {
                int index = scheduler.selectNextIndex(readyQueue);
                if (index != -1) {
                    running = readyQueue.removeAt(index);
                    running.setState(ProcessState.RUNNING);
                    quantumUsed = 0;
                    say("  despacho: " + running.getName());
                }
            }

            if (running != null) {
                running.executeInstruction();
                quantumUsed++;
                say("  CPU: " + running.getName() + " | restantes " + running.getRemainingTime()
                        + " | quantum usado " + quantumUsed);

                if (running.isFinished()) {
                    running.setState(ProcessState.TERMINATED);
                    order.append(finished == 0 ? "" : ",").append(running.getName());
                    finished++;
                    say("  fin: " + running.getName());
                    running = null;
                    quantumUsed = 0;
                } else if (scheduler.shouldPreempt(running, readyQueue, quantumUsed)) {
                    running.setState(ProcessState.READY);
                    say("  expropiado: " + running.getName());
                    readyQueue.add(running);
                    preemptions++;
                    running = null;
                    quantumUsed = 0;
                }
            }

            if (VERBOSE) {
                StringBuilder queue = new StringBuilder("  listos: [ ");
                for (int i = 0; i < readyQueue.getSize(); i++) {
                    queue.append(readyQueue.get(i).getName()).append(' ');
                }
                System.out.println(queue.append(']'));
            }
        }
        return new Result(order.toString(), finished, cycle, preemptions);
    }

    private static void say(String text) {
        if (VERBOSE) {
            System.out.println(text);
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