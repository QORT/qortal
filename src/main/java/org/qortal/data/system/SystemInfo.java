package org.qortal.data.system;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class SystemInfo {

    private long freeMemory;

    private long memoryInUse;

    private long totalMemory;

    private long maxMemory;

    private int availableProcessors;

    public SystemInfo() {
    }

    public SystemInfo(long freeMemory, long memoryInUse, long totalMemory, long maxMemory, int availableProcessors) {
        this.freeMemory = freeMemory;
        this.memoryInUse = memoryInUse;
        this.totalMemory = totalMemory;
        this.maxMemory = maxMemory;
        this.availableProcessors = availableProcessors;
    }

    public long getFreeMemory() {
        return freeMemory;
    }

    public long getMemoryInUse() {
        return memoryInUse;
    }

    public long getTotalMemory() {
        return totalMemory;
    }

    public long getMaxMemory() {
        return maxMemory;
    }

    public int getAvailableProcessors() {
        return availableProcessors;
    }
}
