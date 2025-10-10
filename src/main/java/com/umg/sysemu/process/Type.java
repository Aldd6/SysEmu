package com.umg.sysemu.process;

public enum Type {
    SYSTEM("System"),
    USER("User"),
    BATCH("Batch");

    private final String description;

    Type(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return description;
    }
}
