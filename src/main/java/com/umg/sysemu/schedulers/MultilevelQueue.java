package com.umg.sysemu.schedulers;

import com.umg.sysemu.process.PCB;
import com.umg.sysemu.process.Status;

import java.util.ArrayList;
import java.util.List;

public class MultilevelQueue implements IScheduler{
    private List<PCB> sysQueue;
    private List<PCB> userQueue;
    private List<PCB> batchQueue;
    private boolean inUseFlagSys;
    private boolean inUseFlagUser;
    private boolean inUseFlagBatch;

    private int sysQuantum;
    private int userQuantum;

    private IScheduler rrSystem;
    private IScheduler rrUser;
    private IScheduler fcsBatch;

    public MultilevelQueue(int sysQuantum, int userQuantum) {
        this.sysQueue = new ArrayList<>();
        this.userQueue = new ArrayList<>();
        this.batchQueue = new ArrayList<>();
        this.inUseFlagSys = false;
        this.inUseFlagUser = false;
        this.inUseFlagBatch = false;

        this.sysQuantum = sysQuantum;
        this.userQuantum = userQuantum;

        this.rrSystem = new RoundRobin(this.sysQuantum);
        this.rrUser = new RoundRobin(this.userQuantum);
        this.fcsBatch = new FCFS();
    }

    @Override
    public void execute(List<PCB> readyQueue) {
        while(!readyQueue.isEmpty()) {
            PCB p = readyQueue.removeFirst();
            if(p.getStatus() == Status.TERMINATED) continue;
            switch(p.getProcessType()) {
                case SYSTEM -> sysQueue.add(p);
                case USER -> userQueue.add(p);
                case BATCH -> batchQueue.add(p);
            }
        }

        sysQueue.removeIf(p -> p.getStatus() == Status.TERMINATED);
        userQueue.removeIf(p -> p.getStatus() == Status.TERMINATED);
        batchQueue.removeIf(p -> p.getStatus() == Status.TERMINATED);

        if(isAllQueuesEmpty() && !isCpuBusy()) return;

        rrSystem.execute(sysQueue);
        inUseFlagSys = rrSystem.isCpuBusy();
        if(!sysQueue.isEmpty() || inUseFlagSys) return;

        rrUser.execute(userQueue);
        inUseFlagUser = rrUser.isCpuBusy();
        if(!userQueue.isEmpty() || inUseFlagUser) return;

        fcsBatch.execute(batchQueue);
        inUseFlagBatch = fcsBatch.isCpuBusy();
    }

    @Override
    public boolean isCpuBusy() { return inUseFlagSys || inUseFlagUser || inUseFlagBatch; }

    @Override
    public void printResults() {
        System.out.println("--------- SYSTEM QUEUE ---------");
        rrSystem.printResults();
        System.out.println("--------- USER QUEUE ---------");
        rrUser.printResults();
        System.out.println("--------- BATCH QUEUE ---------");
        fcsBatch.printResults();
    }

    private boolean isAllQueuesEmpty() { return sysQueue.isEmpty() && userQueue.isEmpty() && batchQueue.isEmpty(); }
}
