package com.stratuslite.fleet;

public record ResourceVector(
        int cpuCores,
        int memoryGb,
        int storageGb,
        int iops
) {

    public ResourceVector {
        if (cpuCores < 0 || memoryGb < 0 || storageGb < 0 || iops < 0) {
            throw new IllegalArgumentException("resource values must be non-negative");
        }
    }

    public boolean canFit(ResourceVector demand) {
        return cpuCores >= demand.cpuCores
                && memoryGb >= demand.memoryGb
                && storageGb >= demand.storageGb
                && iops >= demand.iops;
    }

    public ResourceVector plus(ResourceVector other) {
        return new ResourceVector(
                cpuCores + other.cpuCores,
                memoryGb + other.memoryGb,
                storageGb + other.storageGb,
                iops + other.iops
        );
    }

    public ResourceVector minus(ResourceVector other) {
        return new ResourceVector(
                cpuCores - other.cpuCores,
                memoryGb - other.memoryGb,
                storageGb - other.storageGb,
                iops - other.iops
        );
    }

    public double averageUtilizationAgainst(ResourceVector total) {
        return (ratio(cpuCores, total.cpuCores)
                + ratio(memoryGb, total.memoryGb)
                + ratio(storageGb, total.storageGb)
                + ratio(iops, total.iops)) / 4.0;
    }

    public double maxUtilizationAgainst(ResourceVector total) {
        return Math.max(
                Math.max(ratio(cpuCores, total.cpuCores), ratio(memoryGb, total.memoryGb)),
                Math.max(ratio(storageGb, total.storageGb), ratio(iops, total.iops))
        );
    }

    public double utilizationVarianceAgainst(ResourceVector total) {
        double cpu = ratio(cpuCores, total.cpuCores);
        double memory = ratio(memoryGb, total.memoryGb);
        double storage = ratio(storageGb, total.storageGb);
        double iopsRatio = ratio(iops, total.iops);
        double average = (cpu + memory + storage + iopsRatio) / 4.0;

        return (square(cpu - average)
                + square(memory - average)
                + square(storage - average)
                + square(iopsRatio - average)) / 4.0;
    }

    private static double ratio(int used, int total) {
        if (total == 0) {
            return used == 0 ? 0.0 : 1.0;
        }
        return (double) used / total;
    }

    private static double square(double value) {
        return value * value;
    }
}
