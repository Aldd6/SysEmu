package com.umg.sysemu;

import com.umg.sysemu.UI.DTO.ProcessRow;
import com.umg.sysemu.UI.DTO.TimelineSlice;
import com.umg.sysemu.kernel.*;
import com.umg.sysemu.process.PCB;
import com.umg.sysemu.process.Status;
import com.umg.sysemu.process.Type;
import com.umg.sysemu.schedulers.*;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        Kernel krnl = new Kernel(
                () -> new MainMemory(4024),
                () -> new VirtualMemory(),
                () -> new MLTermScheduler(MLTermScheduler.VictimPolicy.LOW_PRIORITY_FIRST),
                () -> new RoundRobin(3)
        );

        krnl.loadJobsAtBoot("C:\\Programs\\IntelliJ\\SysEmu\\src\\main\\java\\com\\umg\\sysemu\\assets\\MemoryOne.txt");

        while(!krnl.isFinished()) {
            krnl.step();
//            System.out.println("---------------------------------------------> RAM OUT");
//            for(PCB p : krnl.getQueues().get("VM_SUSPENDED")) {
//                System.out.println(p.getPid() + " " + p.getStatus());
//            }
//            System.out.println("RAM IN <--------------------------------------------- ");
        }

        System.out.println(krnl.getAverages());
        for(ProcessRow t : krnl.getProcessTable()) {
            System.out.println(t);
        }


        System.out.println("Process Terminated");
    }

}
