package com.umg.sysemu.UI.DTO;

public record TimelineSlice(
        int startTick, int endTick, Long pid, String scheduler, String queue
) {}
