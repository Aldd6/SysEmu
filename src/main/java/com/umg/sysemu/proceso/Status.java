package com.umg.sysemu.proceso;

public enum Status {
    NEW("NEW"),
    READY("READY"),
    RUNNING("RUNNING"),
    SUSPENDED("SUSPENDED"),
    BLOCKED("BLOCKED"),
    TERMINATED("TERMINATED"),;

    private final String description;

    Status(String description) {this.description = description;}

    @Override
    public String toString() { return description; }
}
