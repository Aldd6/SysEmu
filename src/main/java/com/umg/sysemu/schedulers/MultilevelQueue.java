package com.umg.sysemu.schedulers;

import com.umg.sysemu.process.PCB;
import com.umg.sysemu.process.Status;

import java.util.*;

public class MultilevelQueue implements IScheduler,RunningInspector{
    private List<PCB> sysQueue;
    private List<PCB> userQueue;
    private List<PCB> batchQueue;

    private Set<Long> sysSet;
    private Set<Long> userSet;
    private Set<Long> batchSet;
    private Set<Long> ramPids;

    private boolean inUseFlagSys;
    private boolean inUseFlagUser;
    private boolean inUseFlagBatch;

    private int sysQuantum;
    private int userQuantum;

    private Long curPid;
    private String curLane;

    private RoundRobin rrSystem;
    private RoundRobin rrUser;
    private FCFS fcsBatch;

    public MultilevelQueue(int sysQuantum, int userQuantum) {
        this.sysQueue = new ArrayList<>();
        this.userQueue = new ArrayList<>();
        this.batchQueue = new ArrayList<>();

        this.sysSet = new HashSet<>();
        this.userSet = new HashSet<>();
        this.batchSet = new HashSet<>();
        this.ramPids = new HashSet<>();

        this.inUseFlagSys = false;
        this.inUseFlagUser = false;
        this.inUseFlagBatch = false;

        this.sysQuantum = sysQuantum;
        this.userQuantum = userQuantum;

        this.curPid = null;
        this.curLane = null;

        this.rrSystem = new RoundRobin(this.sysQuantum);
        this.rrUser = new RoundRobin(this.userQuantum);
        this.fcsBatch = new FCFS();
    }

    @Override
    public void execute(List<PCB> readyQueue) {

        ramPids.clear();
        for(PCB p : readyQueue) {
            if(p.getStatus() != Status.TERMINATED) ramPids.add(p.getPid());
        }

        for(PCB p : readyQueue) {
            if(p.getStatus() != Status.READY) continue;
            switch (p.getProcessType()) {
                case SYSTEM -> {
                    if(sysSet.add(p.getPid())) sysQueue.addLast(p);
                }
                case USER -> {
                    if(userSet.add(p.getPid())) userQueue.addLast(p);
                }
                case BATCH -> {
                    if(batchSet.add(p.getPid())) batchQueue.addLast(p);
                }
            }
        }

        clean(sysQueue,sysSet);
        clean(userQueue,userSet);
        clean(batchQueue,batchSet);

        if(isAllQueuesEmpty() && !isCpuBusy()) {
            updateInspector();
            return;
        }

        if(rrUser.isCpuBusy() && !sysQueue.isEmpty()) {
            rrUser.preempt(userQueue);
        }

        if(fcsBatch.isCpuBusy() && !sysQueue.isEmpty()) {
            fcsBatch.preempt(batchQueue);
        }

        rrSystem.execute(sysQueue);
        inUseFlagSys = rrSystem.isCpuBusy();
        updateInspector();
        if(!sysQueue.isEmpty() || inUseFlagSys) return;

        if(fcsBatch.isCpuBusy() && !userQueue.isEmpty()) {
            fcsBatch.preempt(batchQueue);
        }

        rrUser.execute(userQueue);
        inUseFlagUser = rrUser.isCpuBusy();
        updateInspector();
        if(!userQueue.isEmpty() || inUseFlagUser) return;

        fcsBatch.execute(batchQueue);
        inUseFlagBatch = fcsBatch.isCpuBusy();
        updateInspector();
    }

    @Override
    public boolean isCpuBusy() { return inUseFlagSys || inUseFlagUser || inUseFlagBatch; }

    private boolean isAllQueuesEmpty() { return sysQueue.isEmpty() && userQueue.isEmpty() && batchQueue.isEmpty(); }

    private void clean(List<PCB> queue, Set<Long> set) {
        for(Iterator<PCB> it = queue.iterator(); it.hasNext(); ) {
            PCB p = it.next();
            long pid = p.getPid();
            if(p.getStatus() != Status.READY || !ramPids.contains(pid)) {
                it.remove();
                set.remove(pid);
            }
        }
    }

    @Override
    public Long currentPid() { return curPid; }
    @Override
    public String currentLane() { return curLane; }

    @Override
    public void printResults() {
        System.out.println("--------- SYSTEM QUEUE ---------");
        rrSystem.printResults();
        System.out.println("--------- USER QUEUE ---------");
        rrUser.printResults();
        System.out.println("--------- BATCH QUEUE ---------");
        fcsBatch.printResults();
    }

    private void updateInspector() {
        curPid = null;
        curLane = null;

        if (rrSystem instanceof RunningInspector riS) {
            Long p = riS.currentPid();
            if (p != null) { curPid = p; curLane = "SYSTEM"; return; }
        }
        if (rrUser instanceof RunningInspector riU) {
            Long p = riU.currentPid();
            if (p != null) { curPid = p; curLane = "USER"; return; }
        }
        if (fcsBatch instanceof RunningInspector riB) {
            Long p = riB.currentPid();
            if (p != null) { curPid = p; curLane = "BATCH"; }
        }
    }

}
