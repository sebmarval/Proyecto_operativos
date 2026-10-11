/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.avilaos.model;
import com.avilaos.enums.ProcessType;
import com.avilaos.enums.ProcessState;
import com.avilaos.enums.BlockReason;


/**
 *
 * @author aleja
 */
public class ProcessControlBlock {

    private static int idCounter = 1;

    private final int id;
    private final String name;
    private int assignedComputerId;
    private ProcessState state;
    private final ProcessType type;
    private int priority;
    private final int memoryRequired;
    private int deadline;
    private int remainingTime;
    private BlockReason blockReason;
    private final ThreadControlBlock tcb;

    public ProcessControlBlock(String name, int assignedComputerId, ProcessType type, 
                               int priority, int memoryRequired, int deadline, int totalInstructions) {
        this.id = idCounter++;
        this.name = name;
        this.assignedComputerId = assignedComputerId;
        this.state = ProcessState.NEW;
        this.type = type;
        this.priority = priority;
        this.memoryRequired = memoryRequired;
        this.deadline = deadline;
        this.remainingTime = totalInstructions;
        this.blockReason = BlockReason.NONE;
        this.tcb = new ThreadControlBlock("T-" + this.id);
    }

    public void executeCycle() {
        if (this.remainingTime > 0) {
            this.remainingTime--;
            this.tcb.incrementPC();
        }
        if (this.deadline > 0) {
            this.deadline--;
        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getAssignedComputerId() {
        return assignedComputerId;
    }

    public void setAssignedComputerId(int assignedComputerId) {
        this.assignedComputerId = assignedComputerId;
    }

    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
        this.tcb.setState(state);
    }

    public ProcessType getType() {
        return type;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getMemoryRequired() {
        return memoryRequired;
    }

    public int getDeadline() {
        return deadline;
    }

    public void setDeadline(int deadline) {
        this.deadline = deadline;
    }

    public int getRemainingTime() {
        return remainingTime;
    }

    public BlockReason getBlockReason() {
        return blockReason;
    }

    public void setBlockReason(BlockReason blockReason) {
        this.blockReason = blockReason;
    }

    public ThreadControlBlock getTcb() {
        return tcb;
    }
}
