package com.umg.sysemu;

import com.umg.sysemu.kernel.Clock;
import com.umg.sysemu.process.PCB;
import com.umg.sysemu.process.Status;
import com.umg.sysemu.schedulers.IScheduler;
import com.umg.sysemu.schedulers.RoundRobin;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<PCB> readyQueue = new LinkedList<>();

        // Define procesos (BT y lo que sea tu segundo parámetro)
        PCB p1 = new PCB(4,5); p1.setArrivalTimeAt(0); p1.assignPid();
        PCB p2 = new PCB(3,7); p2.setArrivalTimeAt(1); p2.assignPid();
        PCB p3 = new PCB(2,4); p3.setArrivalTimeAt(3); p3.assignPid();
        PCB p4 = new PCB(1,6); p4.setArrivalTimeAt(5); p4.assignPid();

        // Lista total para saber cuándo terminamos
        List<PCB> all = Arrays.asList(p1, p2, p3, p4);
        all.sort(Comparator.comparingInt(PCB::getArrivalTime));
        int next = 0; // índice de próxima llegada

        IScheduler rr = new RoundRobin(3);

        while (!allTerminated(all)) {
            int t = Clock.time();

            // 1) Admisión: todos los que llegan en este tick
            while (next < all.size() && all.get(next).getArrivalTime() == t) {
                PCB newcomer = all.get(next);
                newcomer.changeStatus(Status.READY);
                readyQueue.add(newcomer);
                next += 1;
            }

            // 2) Ejecuta EXACTAMENTE un tick (RR debe manejar CPU idle / en uso)
            rr.execute(readyQueue);

            // (opcional) Trace para detectar off-by-one
            //System.out.printf("t=%d rq=%s\n", t, dump(readyQueue));

            // 3) Avanza el tiempo AL FINAL del tick
            Clock.forward();
        }

        rr.printResults();
    }

    private static boolean allTerminated(List<PCB> procs) {
        for (PCB p : procs) if (p.getStatus() != Status.TERMINATED) return false;
        return true;
    }

}
