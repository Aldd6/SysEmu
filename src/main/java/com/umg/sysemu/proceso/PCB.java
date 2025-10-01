package com.umg.sysemu.proceso;

public class PCB implements Comparable<PCB>{
    private long pid;
    private Status status;
    private int priority;
    private int cpuBursts; //Numero de rafagas a ejecutar
    private int arrivalTime; //Tiempo en que entra un proceso a cola de listos
    private int completionTime; //Tiempo en que se finaliza las rafagas de un proceso
    private int attentionTime; //Tiempo en que se atiende por primera vez un proceso

    private int turnaroundTime;
    private int waitingTime;
    private int responseTime;

    public PCB(int priority, int cpuBursts) {
        this.priority = priority;
        this.cpuBursts = cpuBursts;
        this.status = Status.NEW;
        this.arrivalTime = -1;
        this.completionTime = -1;
        this.attentionTime = -1;
        this.turnaroundTime = -1;
        this.waitingTime = -1;
        this.responseTime = -1;
    }

    public void assignPid() { this.pid = System.nanoTime(); }
    public long getPid() { return this.pid; }
    public void setArrivalTimeAt(int arrivalTime) { this.arrivalTime = arrivalTime; }
    public int getArrivalTime() { return this.arrivalTime; }
    public void setCompletionTimeAt(int completionTime) { this.completionTime = completionTime; }
    public int getCompletionTime() { return this.completionTime; }
    public void setAttentionTimeAt(int attentionTime) { this.attentionTime = attentionTime; }
    public int getAttentionTime() { return this.attentionTime; }

    public int calculateTurnaroundTime() {
        this.turnaroundTime = completionTime - arrivalTime;
        return this.turnaroundTime;
    }
    public int getTurnaroundTime() { return this.turnaroundTime; }
    public int calculateWaitingTime() {
        this.waitingTime = turnaroundTime - cpuBursts;
        return this.waitingTime;
    }
    public int getWaitingTime() { return this.waitingTime; }
    public int calculateResponseTime() {
        this.responseTime = attentionTime - arrivalTime;
        return this.responseTime;
    }
    public int getResponseTime() { return this.responseTime; }

    public void changeStatus(Status newStatus) { this.status = newStatus; }
    public Status getStatus() { return this.status; }

    public int consumeCpuBurst() {
        if(this.cpuBursts > 0) return this.cpuBursts--;
        return -1;
    }

    public int incrementPriority() {
        if(this.priority < 99) return this.priority++;
        return 99;
    }
    public int decrementPriority() {
        if(this.priority > 1) return this.priority--;
        return 1;
    }

    @Override
    public int compareTo(PCB o) { return Integer.compare(this.priority, o.priority); }
}
