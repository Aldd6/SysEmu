package com.umg.sysemu.kernel;

import com.umg.sysemu.process.PCB;
import com.umg.sysemu.process.Status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MLTermScheduler {
    public enum VictimPolicy { LOW_PRIORITY_FIRST }

    private VictimPolicy policy;
    private final int SWAP_INTERVAL_TICKS = 5;
    private final int MIN_RESIDENCY_TICKS = 3;
    private final int AGING_INTERVAL_TICKS = 10;
    private final int AGING_STEP = 3;

    private int lastSwapTick = Integer.MIN_VALUE;

    private Map<Long,Integer> inRamSince;
    private Map<Long, Integer> suspendedSince;

    public MLTermScheduler(VictimPolicy policy) {
        this.policy = policy;
        this.inRamSince = new HashMap<>();
        this.suspendedSince = new HashMap<>();
    }

    public void execute(int tick, MainMemory mm, VirtualMemory vm) {
        applyAgingToSuspended(vm, tick);

        loadWhileFits(mm,vm,VirtualMemory.VirtualQueue.NEW,tick);
        loadWhileFits(mm,vm,VirtualMemory.VirtualQueue.SUSPENDED,tick);

        boolean needNew = !vm.viewNewQueue().isEmpty();
        boolean needSuspend = !vm.viewSuspendedQueue().isEmpty();
        boolean canSwap = (tick - lastSwapTick) >= SWAP_INTERVAL_TICKS;

        if((needNew || needSuspend) && canSwap) {
            boolean evictAndAdmit = false;
            evictAndAdmit |= admitWithEviction(mm,vm,VirtualMemory.VirtualQueue.NEW,tick);
            evictAndAdmit |= admitWithEviction(mm,vm,VirtualMemory.VirtualQueue.SUSPENDED,tick);
            if(evictAndAdmit) lastSwapTick = tick;
        }
    }

    private void loadWhileFits(MainMemory mm, VirtualMemory vm, VirtualMemory.VirtualQueue q, int tick) {
        while(true) {
            PCB cand = vm.peek(q);
            if(cand == null) break;
            if(mm.getFreeMemory() < cand.getRamSize()) break;

            cand = vm.deallocate(q);
            if(cand == null) break;

            cand.changeStatus(Status.READY);
            boolean entryOk = mm.allocate(cand);
            if(!entryOk) {
                cand.changeStatus(q == VirtualMemory.VirtualQueue.NEW ? Status.NEW:Status.SUSPENDED);
                vm.allocate(cand);
                break;
            }
            inRamSince.put(cand.getPid(),tick);
            suspendedSince.remove(cand.getPid());

            stampFirstArrival(cand,tick);
        }
    }

    private boolean admitWithEviction(MainMemory mm, VirtualMemory vm, VirtualMemory.VirtualQueue q, int tick) {
        boolean any = false;

        while(true) {
            PCB cand = vm.peek(q);
            if(cand == null) break;

            int need = cand.getRamSize();
            if(mm.getFreeMemory() >= need) {
                cand = vm.deallocate(q);
                cand.changeStatus(Status.READY);
                if(!mm.allocate(cand)) {
                    cand.changeStatus(q == VirtualMemory.VirtualQueue.NEW ? Status.NEW:Status.SUSPENDED);
                    vm.allocate(cand);
                    break;
                }
                inRamSince.put(cand.getPid(),tick);
                suspendedSince.remove(cand.getPid());
                any = true;

                stampFirstArrival(cand,tick);
                continue;
            }
            List<PCB> victims = pickVictims(mm.viewReadyQueue(), need - mm.getFreeMemory(), tick);
            if(victims.isEmpty()) break;

            for(PCB victim : victims) {
                PCB out = mm.deallocate(victim);
                if(out != null) {
                    out.changeStatus(Status.SUSPENDED);
                    vm.allocate(out);
                    inRamSince.remove(victim.getPid());
                    suspendedSince.put(out.getPid(),tick);
                }
            }

            if(mm.getFreeMemory() >= need) {
                cand = vm.deallocate(q);
                cand.changeStatus(Status.READY);
                if(!mm.allocate(cand)) {
                    cand.changeStatus(q == VirtualMemory.VirtualQueue.NEW ? Status.NEW:Status.SUSPENDED);
                    vm.allocate(cand);
                    break;
                }
                inRamSince.put(cand.getPid(),tick);
                suspendedSince.remove(cand.getPid());
                any = true;

                stampFirstArrival(cand,tick);
            }else {
                break;
            }
        }
        return any;
    }

    private List<PCB> pickVictims(List<PCB> readyQueue, int memoryNeeded, int tick) {
        List<PCB> cands = new ArrayList<>();
        for(PCB p : readyQueue) {
            if(p.getStatus() != Status.READY) continue;
            int since = inRamSince.getOrDefault(p.getPid(),Integer.MIN_VALUE);
            if(tick - since < MIN_RESIDENCY_TICKS) continue;
            cands.add(p);
        }
        cands.sort((a,b) -> {
            int byPriority = Integer.compare(a.getPriority(), b.getPriority());
            if(byPriority != 0) return byPriority;
            int bySize = Integer.compare(b.getRamSize(), a.getRamSize());
            if(bySize != 0) return bySize;
            int aSince = inRamSince.getOrDefault(a.getPid(),Integer.MIN_VALUE);
            int bSince = inRamSince.getOrDefault(b.getPid(),Integer.MIN_VALUE);
            return Integer.compare(aSince, bSince);
        });

        int acc = 0;
        List<PCB> victims = new ArrayList<>();
        for(PCB p : cands) {
            victims.add(p);
            acc += p.getRamSize();
            if(acc >= memoryNeeded) break;
        }
        return victims;
    }

    private void applyAgingToSuspended(VirtualMemory vm, int tick) {
        for(PCB p : vm.viewSuspendedQueue()) {
            Long pid = p.getPid();
            int last = suspendedSince.getOrDefault(pid, tick);
            if(tick - last >= AGING_INTERVAL_TICKS) {
                for(int k = 0; k < AGING_STEP; k++) p.incrementPriority();
                suspendedSince.put(pid, tick);
            }
        }
    }

    private void stampFirstArrival(PCB p, int tick) {
        if(p.getArrivalTime() < 0) p.setArrivalTimeAt(tick);
    }
}
