package com.umg.sysemu.schedulers;

import com.umg.sysemu.kernel.Clock;
import com.umg.sysemu.process.PCB;
import com.umg.sysemu.process.Status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoundRobin implements IScheduler,PreemptPolicy, RunningInspector{
    private List<PCB> ganttChart;
    private Map<Long,Integer> quantumTracker;
    private PCB processInUse;
    private Long pidInUse;
    private boolean inUseFlag;
    private int quantum;

    public RoundRobin(int quantum) {
        this.quantum = quantum;
        this.processInUse = null;
        this.pidInUse = null;
        this.ganttChart = new ArrayList<>();
        this.quantumTracker = new HashMap<Long,Integer>();
        this.inUseFlag = false;
    }

    @Override
    public void execute(List<PCB> readyQueue) {
        if(!inUseFlag) {
            if(readyQueue.isEmpty()) return;

            //NEW PROCESS INBOUND
            processInUse = readyQueue.removeFirst();

            pidInUse = processInUse.getPid();
            processInUse.changeStatus(Status.RUNNING);

            if(!quantumTracker.containsKey(pidInUse)) {
                quantumTracker.put(pidInUse,0);
                processInUse.setAttentionTimeAt(Clock.time());
            }

            inUseFlag = true;
        }
        int remaining = processInUse.consumeCpuBurst();
        int used = quantumTracker.get(pidInUse) + 1;
        quantumTracker.put(pidInUse,used);

        if(remaining == 0) {
            processInUse.changeStatus(Status.TERMINATED);
            processInUse.setCompletionTimeAt(Clock.time() + 1);
            quantumTracker.remove(pidInUse);
            ganttChart.add(processInUse);

            processInUse = null;
            pidInUse = null;
            inUseFlag = false;
            return;
        }

        if(used == quantum) {
            quantumTracker.put(pidInUse,0);
            processInUse.changeStatus(Status.READY);
            readyQueue.addLast(processInUse);

            processInUse = null;
            pidInUse = null;
            inUseFlag = false;
        }
    }

    @Override
    public boolean isCpuBusy() { return inUseFlag; }

    @Override
    public void preempt(List<PCB> ownerQueue) {
        if(!inUseFlag) return;
        processInUse.changeStatus(Status.READY);
        ownerQueue.addFirst(processInUse);
        processInUse = null;
        pidInUse = null;
        inUseFlag = false;
    }

    @Override
    public Long currentPid() { return inUseFlag && processInUse != null ? processInUse.getPid() : null; }

    public void printResults() {
        for(PCB p : ganttChart) {
            System.out.println("PROCESS: " + p.getPid());
            System.out.println("COMPLETION TIME: " + p.getCompletionTime());
            System.out.println("TURNAROUND TIME: " + p.calculateTurnaroundTime());
            System.out.println("WAITING TIME: " + p.calculateWaitingTime());
            System.out.println();
        }
    }
}
