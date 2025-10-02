package com.umg.sysemu.schedulers;

import com.umg.sysemu.kernel.Clock;
import com.umg.sysemu.process.PCB;
import com.umg.sysemu.process.Status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoundRobin implements IScheduler{
    private List<Long> ganttChart;
    private Map<Long,Integer> quantumTracker;
    private PCB processInUse;
    private Long pidInUse;
    private boolean inUseFlag;
    private int quantum;

    public RoundRobin(int quantum) {
        this.quantum = quantum;
        this.processInUse = null;
        this.pidInUse = null;
        this.ganttChart = new ArrayList<Long>();
        this.quantumTracker = new HashMap<Long,Integer>();
        this.inUseFlag = false;
    }

    @Override
    public void execute(List<PCB> readyQueue) {
        if(!inUseFlag) {
            //NEW PROCESS INBOUND
            processInUse = readyQueue.getFirst();
            //if(processInUse.getArrivalTime() != Clock.time()) return;

            pidInUse = processInUse.getPid();
            processInUse.changeStatus(Status.RUNNING);

            if(!quantumTracker.containsKey(pidInUse)) {
                quantumTracker.put(pidInUse,1);
                processInUse.setAttentionTimeAt(Clock.time());
            }
            processInUse.consumeCpuBurst();

            inUseFlag = true;
        }else {
            int lastQuantum = quantumTracker.get(pidInUse);
            if(lastQuantum + 1 <= quantum) {
                if(processInUse.consumeCpuBurst() > 0) {
                    //PROCESS IN USE
                    int newQuantum = quantumTracker.get(pidInUse) + 1;
                    quantumTracker.put(pidInUse,newQuantum);
                }else {
                    //PROCESS TERMINATED
                    processInUse.changeStatus(Status.TERMINATED);
                    processInUse.setCompletionTimeAt(Clock.time());
                    inUseFlag = false;
                }
            }else {
                //PROCESS NOT IN USE BUT WITH WORK TO DO
                quantumTracker.put(pidInUse,0);
                processInUse.changeStatus(Status.READY);

                processInUse = null;
                pidInUse = null;

                PCB lastProcess = readyQueue.removeFirst();
                readyQueue.addLast(lastProcess);

                inUseFlag = false;
            }
        }
    }
}
