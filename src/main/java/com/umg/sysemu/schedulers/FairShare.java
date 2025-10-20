package com.umg.sysemu.schedulers;

import com.umg.sysemu.process.PCB;
import com.umg.sysemu.process.Status;

import java.util.*;
import java.util.function.Function;

public class FairShare implements IScheduler{
    private static final int EPOCH_LENGTH = 60;

    private int rrQuantum;

    private Map<String, List<PCB>> queueByUser;
    private Map<String, Set<Long>> seenByUser;

    private Map<String, IScheduler> userPolicy;

    private Map<String, Integer> budget;
    private int epochRemaining;

    private List<String> order;
    private int ptr;

    private Set<Long> ramPids;

    public FairShare(int rrQuantum) {
        this.queueByUser = new HashMap<>();
        this.seenByUser = new HashMap<>();
        this.userPolicy = new HashMap<>();
        this.budget = new HashMap<>();
        this.epochRemaining = 0;
        this.order = new ArrayList<>();
        this.ptr = 0;
        this.ramPids = new HashSet<>();
        this.rrQuantum = rrQuantum;
    }

    @Override
    public void execute(List<PCB> readyQueue) {
        ramPids.clear();
        for (PCB p : readyQueue) {
            if(p.getStatus() != Status.TERMINATED) ramPids.add(p.getPid());
        }

        boolean newActiveUser = false;
        for(PCB p : readyQueue) {
            if(p.getStatus() != Status.READY) continue;
            final String uid = p.getUserId();

            queueByUser.computeIfAbsent(uid, k -> new ArrayList<>());
            seenByUser.computeIfAbsent(uid, k -> new HashSet<>());
            userPolicy.computeIfAbsent(uid, k -> new RoundRobin(rrQuantum));

            if(seenByUser.get(uid).add(p.getPid())) {
                queueByUser.get(uid).addLast(p);
                if(!order.contains(uid)) { order.add(uid); newActiveUser = true; }
            }
        }

        for(var p : queueByUser.entrySet()) {
            String uid = p.getKey();
            List<PCB> queue = p.getValue();
            Set<Long> seen = seenByUser.get(uid);
            for(Iterator<PCB> it = queue.iterator(); it.hasNext(); ) {
                PCB pcb = it.next();
                if(pcb.getStatus() != Status.READY || !ramPids.contains(pcb.getPid())) {
                    it.remove();
                    seen.remove(pcb.getPid());
                }
            }
        }

        if(epochRemaining <= 0 || !isBudgetAvilable() || newActiveUser) {
            resliceEquality();
        }

        String uid = pickNextEligibleUser();
        if(uid == null) return;

        for(String other : order) {
            if(!other.equals(uid)) {
                IScheduler policyOther = userPolicy.get(other);
                if(policyOther.isCpuBusy() && policyOther instanceof PreemptPolicy pre) {
                    pre.preempt(queueByUser.get(other));
                }
            }
        }

        IScheduler policy = userPolicy.get(uid);
        List<PCB> queue = queueByUser.get(uid);
        policy.execute(queue);

        budget.put(uid, Math.max(0, budget.getOrDefault(uid, 0) - 1));
        epochRemaining = Math.max(0, epochRemaining - 1);

        if(budget.get(uid) == 0 && policy.isCpuBusy() && policy instanceof PreemptPolicy pre) {
            pre.preempt(queue);
        }

        if(!order.isEmpty()) ptr = (ptr + 1) % order.size();
    }

    @Override
    public boolean isCpuBusy() {
        for(IScheduler eng : userPolicy.values()) if(eng.isCpuBusy()) return true;
        return false;
    }

    @Override
    public void printResults() {
        for(IScheduler eng : userPolicy.values()) {
            System.out.println("------- USER -------");
            eng.printResults();
        }
    }

    private boolean isBudgetAvilable() {
        for(String uid : order) {
            if(budget.getOrDefault(uid, 0) > 0 &&
                    userPolicy.get(uid).isCpuBusy() ||
                    !queueByUser.get(uid).isEmpty()) return true;
        }
        return false;
    }

    private void resliceEquality() {
        List<String> activeUsers = new ArrayList<>();
        for(String uid : order) {
            if(userPolicy.get(uid).isCpuBusy() || !queueByUser.get(uid).isEmpty()) activeUsers.add(uid);
        }
        budget.clear();
        if(activeUsers.isEmpty()) { epochRemaining = 0; return; }

        int n = activeUsers.size();
        int base = EPOCH_LENGTH / n;
        int remain = EPOCH_LENGTH % n;

        for(String uid : activeUsers) budget.put(uid, base);
        for(int i = 0; i < n; i++) {
            String u = activeUsers.get(i);
            budget.put(u, budget.get(u) + 1);
        }
        epochRemaining = EPOCH_LENGTH;

        int i = 0;
        while(i < order.size() && !activeUsers.contains(order.get(i))) i++;
        if(i < order.size()) ptr = i;
    }

    private String pickNextEligibleUser() {
        if(order.isEmpty()) return null;
        int n = order.size();
        for(int i = 0; i < n; i++) {
            String uid = order.get((ptr + i) % n);
            if(budget.getOrDefault(uid, 0) > 0 &&
                    userPolicy.get(uid).isCpuBusy() || !queueByUser.get(uid).isEmpty()) return uid;
        }
        return null;
    }
}
