package com.umg.sysemu.schedulers;

import com.umg.sysemu.process.PCB;

import java.util.ArrayList;
import java.util.List;

public class UserGroup {
    private List<PCB> group;

    public UserGroup(int algorithmQuantum) {
        this.group = new ArrayList<PCB>();
    }

    public boolean isUserActive() { return !group.isEmpty(); }

    public double getProcessQuantum(double groupShares) {
        return 0;
    }

}
