package com.umg.sysemu.schedulers;

import com.umg.sysemu.kernel.Clock;
import com.umg.sysemu.process.PCB;
import com.umg.sysemu.process.Status;

import java.util.ArrayList;
import java.util.List;

public class FCFS implements IScheduler{
    private List<PCB> ganttChart;
    private PCB processInUse;
    private boolean inUseFlag;


    public FCFS() {
        ganttChart = new ArrayList<PCB>();
        this.inUseFlag = false;
    }

    @Override
    public void execute(List<PCB> readyQueue) {
        if(!inUseFlag) {
            if(readyQueue.isEmpty()) return;

            processInUse = readyQueue.removeFirst();
            processInUse.changeStatus(Status.RUNNING);
            processInUse.setAttentionTimeAt(Clock.time());

            inUseFlag = true;
        }

        int remaining = processInUse.consumeCpuBurst();
        if(remaining == 0) {
            processInUse.setCompletionTimeAt(Clock.time() + 1);
            processInUse.changeStatus(Status.TERMINATED);
            ganttChart.add(processInUse);
            inUseFlag = false;
        }
    }

    @Override
    public boolean isCpuBusy() { return inUseFlag; }

    @Override
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
