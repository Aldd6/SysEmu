package com.umg.sysemu.UI.DTO;

import com.umg.sysemu.process.Status;
import com.umg.sysemu.process.Type;

public record ProcessRow(
        long pid, String user, Type type, int priority, int arrival, int burstTotal, int burstRemaining,
        Status status, Integer firstRun, Integer completion, Integer turnaround, Integer waiting, Integer response
) {}
