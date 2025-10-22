package com.umg.sysemu.kernel;

import com.umg.sysemu.UI.DTO.*;
import com.umg.sysemu.process.PCB;
import com.umg.sysemu.process.Status;
import com.umg.sysemu.schedulers.IScheduler;
import com.umg.sysemu.schedulers.RunningInspector;

import java.util.*;
import java.util.function.Supplier;

public class Kernel {
    private MainMemory ram;
    private VirtualMemory vm;
    private MLTermScheduler mts;
    private IScheduler cpu;

    private final Supplier<MainMemory> ramSupplier;
    private final Supplier<VirtualMemory> vmSupplier;
    private final Supplier<MLTermScheduler> mtsSupplier;
    private final Supplier<IScheduler> cpuSupplier;

    private Long lastPid = null;
    private int lastStart = 0;
    private String lastLane = null;
    private final List<TimelineSlice> timeline = new ArrayList<>();

    private Map<Long,PCB> registry = new HashMap<>();

    private int ticksRunWithPid = 0;
    private int ticksElapsed = 0;

    public Kernel(Supplier<MainMemory> ramSupplier, Supplier<VirtualMemory> vmSupplier,
                  Supplier<MLTermScheduler> mtsSupplier, Supplier<IScheduler> cpuSupplier) {
        this.ramSupplier = ramSupplier;
        this.vmSupplier = vmSupplier;
        this.mtsSupplier = mtsSupplier;
        this.cpuSupplier = cpuSupplier;

        this.ram = ramSupplier.get();
        this.vm = vmSupplier.get();
        this.mts = mtsSupplier.get();
        this.cpu = cpuSupplier.get();
    }

    public void step() {
        final int tick = Clock.time();

        harvestRegistry();

        mts.execute(tick, ram, vm);

        cpu.execute(ram.viewReadyQueue());

        final Long nowPid = findRunningPid();
        final String policyName = getActualPolicyName();
        updateGantt(tick,policyName);

        if(nowPid != null) ticksRunWithPid++;
        ticksElapsed++;

        for(PCB p : new ArrayList<>(ram.viewReadyQueue())) {
            if(p.getStatus() == Status.TERMINATED) {
                ram.deallocate(p);
            }
        }

        Clock.forward();
    }



    public void reset() {

        flushTimelineAtEnd();

        Clock.reset();

        this.ram = this.ramSupplier.get();
        this.vm = this.vmSupplier.get();
        this.mts = this.mtsSupplier.get();
        this.cpu = this.cpuSupplier.get();

        lastPid = null;
        lastStart = 0;
        timeline.clear();
        registry.clear();

        ticksRunWithPid = 0;
        ticksElapsed = 0;
    }

    public boolean isFinished() {
        boolean isRamEmpty = ram.viewReadyQueue().isEmpty();
        boolean isVmEmpty = vm.viewNewQueue().isEmpty() && vm.viewSuspendedQueue().isEmpty();
        boolean isCpuIdle = !cpu.isCpuBusy();
        boolean allTerminated = registry.values().stream()
                .allMatch(p -> p.getStatus() == Status.TERMINATED);
        return allTerminated || (isRamEmpty && isVmEmpty && isCpuIdle);
    }

    public void loadJobsAtBoot(String url) {
        vm.loadAtBoot(url);
        harvestRegistry();
    }

    public void addProcess(PCB p) {
        p.changeStatus(Status.NEW);
        vm.allocate(p);
        registry.put(p.getPid(), p);
    }

    public MemoryView getMemoryView() {
        return new MemoryView(ram.getMemorySize(),ram.getMemoryUsed(),ram.getFreeMemory());
    }

    public List<TimelineSlice> getTimeline() {
        return Collections.unmodifiableList(timeline);
    }

    public List<ProcessRow> getProcessTable() {
        List<ProcessRow> rows = new ArrayList<>(registry.size());
        for(PCB p : registry.values()) {
            rows.add(new ProcessRow(
                    p.getPid(),
                    p.getUserId(),
                    p.getProcessType(),
                    p.getPriority(),
                    p.getArrivalTime(),
                    p.getCpuBurstsTotal(),
                    p.getCpuBursts(),
                    p.getStatus(),
                    p.getAttentionTime(),
                    p.getCompletionTime(),
                    p.getTurnaroundTime(),
                    p.getWaitingTime(),
                    p.getResponseTime()
            ));
        }

        rows.sort(Comparator.comparingInt(ProcessRow::arrival).thenComparingLong(ProcessRow::pid));
        return rows;
    }

    public Averages getAverages() {
        int finished = 0;
        long sumTurnaround = 0;
        long sumResponse = 0;
        long sumWaiting = 0;
        for(PCB p : registry.values()) {
            int turnaround = p.calculateTurnaroundTime();
            int response = p.calculateResponseTime();
            int waiting = p.calculateWaitingTime();
            if(turnaround > 0) sumTurnaround += turnaround;
            if(response > 0) sumResponse += response;
            if(waiting > 0) sumWaiting += waiting;
            finished++;
        }
        double avgTurnaround = finished > 0 ? (double)sumTurnaround / finished : 0;
        double avgResponse = finished > 0 ? (double)sumResponse / finished : 0;
        double avgWaiting = finished > 0 ? (double)sumWaiting / finished : 0;
        return new Averages(avgTurnaround, avgResponse, avgWaiting);
    }

    public String getActualPolicyName() { return cpu.getClass().getSimpleName(); }

    public Map<String,List<Long>> getQueues() {
        Map<String, List<Long>> q = new LinkedHashMap<>();
        q.put("RAM_READY",
                ram.viewReadyQueue().
                        stream().map(PCB::getPid).
                        toList()
        );
        q.put("VM_NEW",
                vm.viewNewQueue().
                        stream().map(PCB::getPid).
                        toList()
        );
        q.put("VM_SUSPENDED",
                vm.viewSuspendedQueue().
                        stream().map(PCB::getPid).
                        toList()
        );
        return q;
    }

    public void setShortTerm(IScheduler newCpuPolicy) { this.cpu = Objects.requireNonNull(newCpuPolicy); }
    public void setRamSize(int bytes) { ram.resizeMemory(bytes); }

    private void harvestRegistry() {
        if(!ram.viewReadyQueue().isEmpty()) for(PCB p : ram.viewReadyQueue()) registry.putIfAbsent(p.getPid(), p);
        if(!vm.viewNewQueue().isEmpty()) for(PCB p: vm.viewNewQueue()) registry.putIfAbsent(p.getPid(), p);
        if(!vm.viewSuspendedQueue().isEmpty()) for(PCB p: vm.viewSuspendedQueue()) registry.putIfAbsent(p.getPid(), p);
    }

    private Long findRunningPid() {
        if (cpu instanceof RunningInspector ri) return ri.currentPid();
        for (PCB p : ram.viewReadyQueue()) if (p.getStatus() == Status.RUNNING) return p.getPid();
        return null;
    }

    private void updateGantt(int tick, String policy) {
        String nowLane = (cpu instanceof  RunningInspector ri) ? ri.currentLane() : null;
        Long nowPid = findRunningPid();
        boolean changed = !Objects.equals(nowPid, lastPid);
        if(changed) {
            if(lastPid != null) {
                timeline.add(new TimelineSlice(lastStart, tick, lastPid,policy,lastLane));

            }
            lastPid = nowPid;
            lastStart = tick;
            lastLane = nowLane;
        }
    }

    public void flushTimelineAtEnd() {
        int tick = Clock.time();
        if(lastPid != null) {
            timeline.add(new TimelineSlice(lastStart,tick,lastPid,cpu.getClass().getSimpleName(),lastLane));
            lastPid = null;
        }
    }
}
