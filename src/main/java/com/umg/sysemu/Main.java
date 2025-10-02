package com.umg.sysemu;

import com.umg.sysemu.kernel.Clock;
import com.umg.sysemu.process.PCB;
import com.umg.sysemu.process.Status;
import com.umg.sysemu.schedulers.IScheduler;
import com.umg.sysemu.schedulers.RoundRobin;

import java.util.LinkedList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<PCB> readyQueue = new LinkedList<>();
        List<PCB> terminated = new LinkedList<>();
        PCB p1 = new PCB(4,5);
        p1.setArrivalTimeAt(0);
        readyQueue.add(p1);
        p1.assignPid();
        PCB p2 = new PCB(3,7);
        p2.setArrivalTimeAt(1);
        p2.assignPid();
        PCB p3 = new PCB(2,4);
        p3.setArrivalTimeAt(3);
        p3.assignPid();
        PCB p4 = new PCB(1,6);
        p4.setArrivalTimeAt(5);
        p4.assignPid();

        IScheduler rr = new RoundRobin(3);
        while(!readyQueue.isEmpty()) {
            rr.execute(readyQueue);
            Clock.forward();
            switch (Clock.time()) {
                case 1:
                    readyQueue.add(p2);
                    break;
                case 3:
                    readyQueue.add(p3);
                    break;
                case 5:
                    readyQueue.add(p4);
                    break;
            }
            System.out.println("RELOJ: " + Clock.time());
            if(readyQueue.getFirst().getStatus() == Status.TERMINATED) terminated.add(readyQueue.removeFirst());
        }

        for(PCB p : terminated) {
            System.out.println("PROCESS: " + p.getPid());
            System.out.println("COMPLETION TIME: " + p.getCompletionTime());
            System.out.println("TURNAROUND TIME: " + p.calculateTurnaroundTime());
            System.out.println("WAITING TIME: " + p.calculateWaitingTime());
            System.out.println();
        }
    }
}
