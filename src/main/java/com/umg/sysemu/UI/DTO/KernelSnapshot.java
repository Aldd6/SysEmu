package com.umg.sysemu.UI.DTO;

import java.util.List;

public record KernelSnapshot(
        int tick, String shortTermPolicy,
        List<ProcessRow> table, Averages averages,
        List<TimelineSlice> timeline,
        QueuesView queues, MemoryView memory
) {}
