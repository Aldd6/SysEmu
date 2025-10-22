package com.umg.sysemu.schedulers;

public interface RunningInspector {
    Long currentPid();
    default String currentLane() {
        return null;
    }
}
