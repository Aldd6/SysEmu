package com.umg.sysemu.schedulers;

import com.umg.sysemu.process.PCB;
import java.util.List;

@FunctionalInterface
public interface IScheduler {
    void execute(List<PCB> readyQueue);
}
