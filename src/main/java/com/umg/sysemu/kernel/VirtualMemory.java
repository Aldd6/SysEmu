package com.umg.sysemu.kernel;

import com.umg.sysemu.process.PCB;
import com.umg.sysemu.process.Status;
import com.umg.sysemu.process.Type;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;

public class VirtualMemory {
    private List<PCB> newQueue;
    private List<PCB> suspendedQueue;

    public enum VirtualQueue {NEW,SUSPENDED}

    public void loadAtBoot(String url) {
        Scanner file = new Scanner(System.in);
        boolean isFirstLine = true;
        PCB pIn = null;
        try {
            file = new Scanner(new File(url));
            while(file.hasNextLine()) {
                if(isFirstLine) {
                    isFirstLine = false;
                    file.nextLine();
                }
                String line = file.nextLine();
                StringTokenizer tokenizer = new StringTokenizer(line,",");
                int priority = Integer.parseInt(tokenizer.nextToken());
                int cpu = Integer.parseInt(tokenizer.nextToken());
                int memory = Integer.parseInt(tokenizer.nextToken());
                String type = tokenizer.nextToken();
                String user = tokenizer.nextToken();
                switch (type.toUpperCase()) {
                    case "SYSTEM" -> pIn = new PCB(priority,cpu,memory, Type.SYSTEM,user.toUpperCase());
                    case "USER" -> pIn = new PCB(priority,cpu,memory, Type.USER,user.toUpperCase());
                    case "BATCH" -> pIn = new PCB(priority,cpu,memory, Type.BATCH,user.toUpperCase());
                    default -> throw new IllegalArgumentException("Invalid instruction in line " + line);
                }
                pIn.changeStatus(Status.NEW);
                newQueue.add(pIn);
            }
        }catch(FileNotFoundException e) {
            System.out.println(e.getMessage() + "\nThe URL specified for the File that contain the job pool has not been found");
        }finally {
            file.close();
        }
    }

    public List<PCB> viewNewQueue() { return this.newQueue; }
    public List<PCB> viewSuspendedQueue() { return this.suspendedQueue; }

    public boolean allocate(PCB p) {
        if(p == null) return false;
        if(newQueue.contains(p)) return true;
        switch (p.getStatus()) {
            case NEW -> {
                newQueue.addLast(p);
                return true;
            }
            case SUSPENDED -> {
                suspendedQueue.addLast(p);
                return true;
            }
            default -> {return false;}
        }
    }

    public PCB deallocate(VirtualQueue queue) {
        switch (queue) {
            case NEW -> { return newQueue.removeFirst();}
            case SUSPENDED -> { return suspendedQueue.removeFirst();}
            default -> {return null;}
        }
    }

    public PCB peek(VirtualQueue queue) {
        return switch(queue) {
            case NEW -> newQueue.getFirst();
            case SUSPENDED -> suspendedQueue.getFirst();
        };
    }

}
