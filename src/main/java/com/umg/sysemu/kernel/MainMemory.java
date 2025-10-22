package com.umg.sysemu.kernel;

import com.umg.sysemu.process.PCB;

import java.util.ArrayList;
import java.util.List;

public class MainMemory {
    private List<PCB> readyQueue;
    private int memorySize;
    private int memoryUsed;

    public MainMemory(int initialRamSize) {
        this.readyQueue = new ArrayList<>();
        this.memorySize = initialRamSize;
        this.memoryUsed = 0;
    }

    public int getMemorySize() { return this.memorySize; }
    public int getMemoryUsed() { return this.memoryUsed; }
    public int getFreeMemory() { return this.memorySize - this.memoryUsed; }

    public List<PCB> viewReadyQueue() { return this.readyQueue; }

    public boolean allocate(PCB p) {
        if(p == null) return false;
        int size = p.getRamSize();
        if(readyQueue.contains(p)) return true;
        if(memoryUsed + size > memorySize) return false;

        if(p.getPid() < 0) p.assignPid();

        memoryUsed += size;
        readyQueue.addLast(p);
        return true;
    }

    public PCB deallocate(PCB p) {
        if(p == null) return null;
        int index = readyQueue.indexOf(p);
        PCB removed = readyQueue.remove(index);
        if(removed != null) {
            int size = Math.max(0, p.getRamSize());
            memoryUsed = Math.max(0, memoryUsed - size);
        }
        return removed;
    }

    public boolean resizeMemory(int newSize) {
        if(newSize < 0) return false;
        this.memorySize = newSize;
        return true;
    }
}
