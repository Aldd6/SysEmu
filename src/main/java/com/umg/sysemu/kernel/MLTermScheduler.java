package com.umg.sysemu.kernel;

import com.umg.sysemu.process.PCB;
import com.umg.sysemu.process.Status;

import java.util.*;

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
        // Escaneo acotado: una vuelta completa como máximo
        int maxScans = (q == VirtualMemory.VirtualQueue.NEW)
                ? vm.viewNewQueue().size()
                : vm.viewSuspendedQueue().size();
        if (maxScans <= 0) return;

        int scans = 0;
        while (scans < maxScans) {
            PCB cand = vm.peek(q);
            if (cand == null) break;

            // Si es imposible (más grande que la RAM total), no bloquees la cabeza
            if (cand.getRamSize() > mm.getMemorySize()) {
                cand = vm.deallocate(q);
                vm.allocate(cand); // re-encóla al final
                scans++;
                continue;
            }

            // ¿Cabe ya?
            if (mm.getFreeMemory() >= cand.getRamSize()) {
                cand = vm.deallocate(q);
                cand.changeStatus(Status.READY);
                if (!mm.allocate(cand)) {
                    // defensa, debería caber
                    cand.changeStatus(q == VirtualMemory.VirtualQueue.NEW ? Status.NEW : Status.SUSPENDED);
                    vm.allocate(cand);
                    break;
                }
                inRamSince.put(cand.getPid(), tick);
                suspendedSince.remove(cand.getPid());
                stampFirstArrival(cand, tick);

                // tras admitir, podemos seguir intentando más (resetea ventana)
                scans = 0;
                // recalcula tamaño para evitar over/under-scan si la cola cambió
                maxScans = (q == VirtualMemory.VirtualQueue.NEW)
                        ? vm.viewNewQueue().size()
                        : vm.viewSuspendedQueue().size();
                continue;
            }

            // No cabe SIN desalojar → rota y prueba el siguiente (evita bloquear por la cabeza)
            PCB head = vm.deallocate(q);
            vm.allocate(head);
            scans++;
        }
    }

    private boolean admitWithEviction(MainMemory mm, VirtualMemory vm, VirtualMemory.VirtualQueue q, int tick) {
        boolean any = false;

        // tamaño de la cola para acotar el escaneo (evita loop infinito)
        int maxScans = (q == VirtualMemory.VirtualQueue.NEW)
                ? vm.viewNewQueue().size()
                : vm.viewSuspendedQueue().size();
        if (maxScans <= 0) return false;

        int scans = 0;

        while (true) {
            PCB cand = vm.peek(q);
            if (cand == null) break;

            // Caso imposible: proceso más grande que la RAM total
            if (cand.getRamSize() > mm.getMemorySize()) {
                // Marcarlo o simplemente rótalo al final para no bloquear la cabeza
                cand = vm.deallocate(q);           // sacar cabeza
                vm.allocate(cand);                 // re-encolar al final (sigue en su mismo estado NEW/SUSPENDED)
                scans++;
                if (scans >= maxScans) break;      // ya revisamos a todos; corta
                continue;
            }

            int need = cand.getRamSize() - mm.getFreeMemory();
            if (need <= 0) {
                // Cabe sin evacuar: admitir
                cand = vm.deallocate(q);
                cand.changeStatus(Status.READY);
                if (!mm.allocate(cand)) {
                    // fallback: restaurar si por alguna razón falló
                    cand.changeStatus(q == VirtualMemory.VirtualQueue.NEW ? Status.NEW : Status.SUSPENDED);
                    vm.allocate(cand);
                    break;
                }
                inRamSince.put(cand.getPid(), tick);
                suspendedSince.remove(cand.getPid());
                stampFirstArrival(cand, tick);    // <<< asegúrate de tener este helper
                any = true;
                scans = 0;                          // éxito → resetea ventana de escaneo
                continue;
            }

            // Intentar con víctimas
            List<PCB> victims = pickVictims(mm.viewReadyQueue(), need, tick);

            if (victims.isEmpty()) {
                // No puedo liberar lo suficiente para ESTA cabeza AHORA → rotar y probar siguiente
                PCB head = vm.deallocate(q);
                vm.allocate(head);
                scans++;
                if (scans >= maxScans) break;      // ya probamos a todos; corta
                continue;
            }

            // Evacuar víctimas seleccionadas
            for (PCB v : victims) {
                PCB out = mm.deallocate(v);
                if (out != null) {
                    out.changeStatus(Status.SUSPENDED);
                    vm.allocate(out);
                    inRamSince.remove(out.getPid());
                    suspendedSince.put(out.getPid(), tick);
                }
            }

            // Reintentar admisión del candidato (ahora debería caber)
            if (mm.getFreeMemory() >= cand.getRamSize()) {
                cand = vm.deallocate(q);
                cand.changeStatus(Status.READY);
                if (!mm.allocate(cand)) {
                    cand.changeStatus(q == VirtualMemory.VirtualQueue.NEW ? Status.NEW : Status.SUSPENDED);
                    vm.allocate(cand);
                    break;
                }
                inRamSince.put(cand.getPid(), tick);
                suspendedSince.remove(cand.getPid());
                stampFirstArrival(cand, tick);
                any = true;
                scans = 0;
            } else {
                // Algo falló al liberar: rota y sigue
                PCB head = vm.deallocate(q);
                vm.allocate(head);
                scans++;
                if (scans >= maxScans) break;
            }
        }
        return any;
    }

    private List<PCB> pickVictims(List<PCB> readyQueue, int memoryNeeded, int tick) {
        List<PCB> cands = new ArrayList<>();
        for (PCB p : readyQueue) {
            if (p.getStatus() != Status.READY) continue;
            int since = inRamSince.getOrDefault(p.getPid(), Integer.MIN_VALUE);
            if (tick - since < MIN_RESIDENCY_TICKS) continue;
            cands.add(p);
        }
        cands.sort((a,b) -> {
            int byPriority = Integer.compare(a.getPriority(), b.getPriority());    // baja prioridad primero
            if (byPriority != 0) return byPriority;
            int bySize = Integer.compare(b.getRamSize(), a.getRamSize());          // más grandes primero
            if (bySize != 0) return bySize;
            int aSince = inRamSince.getOrDefault(a.getPid(), Integer.MIN_VALUE);
            int bSince = inRamSince.getOrDefault(b.getPid(), Integer.MIN_VALUE);
            return Integer.compare(aSince, bSince);                                 // más antiguos primero
        });

        int acc = 0;
        List<PCB> victims = new ArrayList<>();
        for (PCB p : cands) {
            victims.add(p);
            acc += p.getRamSize();
            if (acc >= memoryNeeded) break;
        }
        if (acc < memoryNeeded) return Collections.emptyList(); // <-- evita desalojos inútiles
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
