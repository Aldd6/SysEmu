package com.umg.sysemu.schedulers;

import com.umg.sysemu.process.PCB;
import java.util.List;

@FunctionalInterface
public interface PreemptPolicy {
    void preempt(List<PCB> ownerQueue);
}
