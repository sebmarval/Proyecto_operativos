package com.avilaos.tests;
import com.avilaos.datastructures.CustomLinkedList;
import com.avilaos.enums.ProcessType;
import com.avilaos.enums.SchedulingPolicy;
import com.avilaos.model.ProcessControlBlock;
import com.avilaos.scheduling.SchedulingAlgorithm;
import com.avilaos.scheduling.SchedulerFactory;

import java.util.Scanner;

public class SchedulingTest {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("====================================================");
        System.out.println("   SIMULADOR INTERACTIVO DE PLANIFICACIÓN - ÁvilaOS");
        System.out.println("====================================================\n");

        System.out.println("Seleccione la política que desea probar:");
        System.out.println("1. FCFS (First-Come, First-Served)");
        System.out.println("2. ROUND_ROBIN (Quantum = 2 ciclos)");
        System.out.println("3. EDF (Earliest Deadline First)");
        System.out.println("4. PREEMPTIVE_PRIORITY (Prioridades Expropiativas)");
        System.out.print("Ingrese opción (1-4): ");
        
        int option = scanner.nextInt();
        SchedulingPolicy policy;
        int quantum = 2;

        switch (option) {
            case 1 -> policy = SchedulingPolicy.FCFS;
            case 2 -> policy = SchedulingPolicy.ROUND_ROBIN;
            case 3 -> policy = SchedulingPolicy.EDF;
            case 4 -> policy = SchedulingPolicy.PREEMPTIVE_PRIORITY;
            default -> {
                System.out.println("Opción no válida. Se usará Round Robin por defecto.");
                policy = SchedulingPolicy.ROUND_ROBIN;
            }
        }

        SchedulingAlgorithm scheduler = SchedulerFactory.create(policy, quantum);
        CustomLinkedList<ProcessControlBlock> readyQueue = new CustomLinkedList<>();

        ProcessControlBlock p1 = new ProcessControlBlock("P1-Largo", 0, ProcessType.CPU_BOUND, 3, 128, 30, 5);
        ProcessControlBlock p2 = new ProcessControlBlock("P2-Urgente", 0, ProcessType.CPU_BOUND, 1, 64, 8, 3);
        ProcessControlBlock p3 = new ProcessControlBlock("P3-Medio", 0, ProcessType.CPU_BOUND, 2, 256, 15, 4);

        readyQueue.add(p1);
        readyQueue.add(p2);
        readyQueue.add(p3);

        System.out.println("\n----------------------------------------------------");
        System.out.println("EJECUTANDO POLÍTICA: " + scheduler.getPolicy() + (policy == SchedulingPolicy.ROUND_ROBIN ? " (Quantum=" + quantum + ")" : ""));
        System.out.println("----------------------------------------------------");

        ProcessControlBlock running = null;
        int quantumUsed = 0;
        int currentCycle = 0;

        while (running != null || !readyQueue.isEmpty()) {
            currentCycle++;
            System.out.println("\n[CICLO DE RELOJ #" + currentCycle + "]");

            if (running == null) {
                int selectedIndex = scheduler.selectNextIndex(readyQueue);
                if (selectedIndex != -1) {
                    running = readyQueue.removeAt(selectedIndex);
                    running.setState(com.avilaos.enums.ProcessState.RUNNING);
                    quantumUsed = 0;
                    System.out.println(" -> DESPACHO: " + running.getName() + " sale de Listos y pasa a CPU.");
                }
            }

            if (running != null) {
                running.executeInstruction();
                quantumUsed++;
                System.out.println(" -> CPU: Ejecutando " + running.getName() + " | Inst. Restantes: " + running.getRemainingTime() + " | Quantum usado: " + quantumUsed);

                if (running.isFinished()) {
                    running.setState(com.avilaos.enums.ProcessState.TERMINATED);
                    System.out.println(" -> FIN: " + running.getName() + " TERMINÓ y sale del sistema.");
                    running = null;
                    quantumUsed = 0;
                } 
                else if (scheduler.shouldPreempt(running, readyQueue, quantumUsed)) {
                    running.setState(com.avilaos.enums.ProcessState.READY);
                    System.out.println(" -> EXPROPIACIÓN: " + running.getName() + " sale de CPU y regresa a Listos.");
                    readyQueue.add(running);
                    running = null;
                    quantumUsed = 0;
                }
            }

            System.out.print(" -> Estado de Cola de Listos: [ ");
            for (int i = 0; i < readyQueue.getSize(); i++) {
                System.out.print(readyQueue.get(i).getName() + " ");
            }
            System.out.println("]");
        }

        System.out.println("\n====================================================");
        System.out.println(" SIMULACIÓN FINALIZADA EN " + currentCycle + " CICLOS.");
        System.out.println("====================================================");
    }
}