package com.umg.sysemu.schedulers;

import com.umg.sysemu.process.PCB;
import java.util.List;


public interface IScheduler {
    void execute(List<PCB> readyQueue);
    void printResults();
    boolean isCpuBusy();
}
