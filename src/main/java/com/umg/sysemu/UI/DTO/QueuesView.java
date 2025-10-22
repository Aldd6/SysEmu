package com.umg.sysemu.UI.DTO;

import java.util.List;

public record QueuesView(
        List<Long> ramReady, List<Long> vmNew, List<Long> vmSuspended
) {}
