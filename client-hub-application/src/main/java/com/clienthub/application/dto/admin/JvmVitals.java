package com.clienthub.application.dto.admin;

public record JvmVitals(
        long usedMemoryMb,
        long maxMemoryMb,
        long freeMemoryMb,
        int availableProcessors
) {

    public static JvmVitals current() {
        Runtime runtime = Runtime.getRuntime();
        long totalMb = runtime.totalMemory() / (1024 * 1024);
        long freeMb = runtime.freeMemory() / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);
        return new JvmVitals(
                Math.max(totalMb - freeMb, 0),
                maxMb,
                freeMb,
                runtime.availableProcessors());
    }
}
