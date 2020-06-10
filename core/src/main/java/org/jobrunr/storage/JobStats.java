package org.jobrunr.storage;

import java.time.Instant;

public class JobStats {

    private final Instant timeStamp;
    private final Long total;
    private final Long awaiting;
    private final Long scheduled;
    private final Long enqueued;
    private final Long processing;
    private final Long failed;
    private final Long succeeded;
    private final Long deleted;
    private final int recurringJobs;
    private final int backgroundJobServers;

    public static JobStats empty() {
        return new JobStats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0, 0);
    }

    public JobStats(Long total, Long awaiting, Long scheduled, Long enqueued, Long processing, Long failed, Long succeeded, Long deleted, int recurringJobs, int backgroundJobServers) {
        this.timeStamp = Instant.now();
        this.total = total;
        this.awaiting = awaiting;
        this.scheduled = scheduled;
        this.enqueued = enqueued;
        this.processing = processing;
        this.failed = failed;
        this.succeeded = succeeded;
        this.deleted = deleted;
        this.recurringJobs = recurringJobs;
        this.backgroundJobServers = backgroundJobServers;
    }

    public Instant getTimeStamp() {
        return timeStamp;
    }

    public Long getTotal() {
        return total;
    }

    public Long getAwaiting() {
        return awaiting;
    }

    public Long getScheduled() {
        return scheduled;
    }

    public Long getEnqueued() {
        return enqueued;
    }

    public Long getProcessing() {
        return processing;
    }

    public Long getFailed() {
        return failed;
    }

    public Long getSucceeded() {
        return succeeded;
    }

    public Long getDeleted() {
        return deleted;
    }

    public int getRecurringJobs() {
        return recurringJobs;
    }

    public int getBackgroundJobServers() {
        return backgroundJobServers;
    }
}
