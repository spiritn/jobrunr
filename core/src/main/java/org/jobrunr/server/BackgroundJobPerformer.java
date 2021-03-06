package org.jobrunr.server;

import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.context.JobRunrDashboardLogger;
import org.jobrunr.jobs.filters.JobFilters;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.exceptions.JobNotFoundException;
import org.jobrunr.server.runner.BackgroundJobRunner;
import org.jobrunr.storage.ConcurrentJobModificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static org.jobrunr.jobs.states.StateName.PROCESSING;
import static org.jobrunr.utils.exceptions.Exceptions.hasCause;

public class BackgroundJobPerformer implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundJobPerformer.class);

    private static final AtomicInteger concurrentModificationExceptionCounter = new AtomicInteger();
    private final BackgroundJobServer backgroundJobServer;
    private final JobFilters jobFilters;
    private final Job job;

    public BackgroundJobPerformer(BackgroundJobServer backgroundJobServer, Job job) {
        this.backgroundJobServer = backgroundJobServer;
        this.jobFilters = backgroundJobServer.getJobFilters();
        this.job = job;
    }

    public void run() {
        try {
            backgroundJobServer.getJobZooKeeper().notifyThreadOccupied();
            boolean canProcess = updateJobStateToProcessingRunJobFiltersAndReturnIfProcessingCanStart();
            if (canProcess) {
                runActualJob();
                updateJobStateToSucceededAndRunJobFilters();
            }
        } catch (Exception e) {
            if (isJobDeletedWhileProcessing(e)) {
                // nothing to do anymore as Job is deleted
                return;
            } else if (isJobServerStopped(e)) {
                updateJobStateToFailedAndRunJobFilters("Job processing was stopped as background job server has stopped", e);
                Thread.currentThread().interrupt();
            } else if (isMethodNotFoundException(e)) {
                updateJobStateToFailed("Job Method not found", e);
            } else {
                updateJobStateToFailedAndRunJobFilters("An exception occurred during the performance of the job", e);
            }
        } finally {
            backgroundJobServer.getJobZooKeeper().notifyThreadIdle();
        }
    }

    private boolean updateJobStateToProcessingRunJobFiltersAndReturnIfProcessingCanStart() {
        try {
            job.startProcessingOn(backgroundJobServer);
            saveAndRunStateRelatedJobFilters(job);
            LOGGER.debug("Job(id={}, jobName='{}') processing started", job.getId(), job.getJobName());
            return job.hasState(PROCESSING);
        } catch (ConcurrentJobModificationException e) {
            // processing already started on other server
            LOGGER.trace("Could not start processing job {} - it is already in a newer state (collision {})", job.getId(), concurrentModificationExceptionCounter.incrementAndGet());
            return false;
        }
    }

    private void runActualJob() throws Exception {
        try {
            JobRunrDashboardLogger.setJob(job);
            backgroundJobServer.getJobZooKeeper().startProcessing(job, Thread.currentThread());
            LOGGER.trace("Job(id={}, jobName='{}') is running", job.getId(), job.getJobName());
            jobFilters.runOnJobProcessingFilters(job);
            BackgroundJobRunner backgroundJobRunner = backgroundJobServer.getBackgroundJobRunner(job);
            backgroundJobRunner.run(job);
            jobFilters.runOnJobProcessedFilters(job);
        } finally {
            backgroundJobServer.getJobZooKeeper().stopProcessing(job);
            JobRunrDashboardLogger.clearJob();
        }
    }

    private void updateJobStateToSucceededAndRunJobFilters() {
        try {
            LOGGER.debug("Job(id={}, jobName='{}') processing succeeded", job.getId(), job.getJobName());
            job.succeeded();
            saveAndRunStateRelatedJobFilters(job);
        } catch (Exception badException) {
            LOGGER.error("FATAL - could not update job(id={}, jobName='{}') to SUCCEEDED state", job.getId(), job.getJobName(), badException);
        }
    }

    private void updateJobStateToFailed(String message, Exception e) {
        try {
            LOGGER.warn("Job(id={}, jobName='{}') processing failed: {}", job.getId(), job.getJobName(), message, e);
            job.failed(message, e);
            this.backgroundJobServer.getStorageProvider().save(job);
        } catch (Exception badException) {
            LOGGER.error("FATAL - could not update job(id={}, jobName='{}') to FAILED state", job.getId(), job.getJobName(), badException);
        }
    }

    private void updateJobStateToFailedAndRunJobFilters(String message, Exception e) {
        try {
            LOGGER.warn("Job(id={}, jobName='{}') processing failed: {}", job.getId(), job.getJobName(), message, e);
            job.failed(message, e);
            saveAndRunStateRelatedJobFilters(job);
        } catch (Exception badException) {
            LOGGER.error("FATAL - could not update job(id={}, jobName='{}') to FAILED state", job.getId(), job.getJobName(), badException);
        }
    }

    protected void saveAndRunStateRelatedJobFilters(Job job) {
        jobFilters.runOnStateAppliedFilters(job);
        StateName beforeStateElection = job.getState();
        jobFilters.runOnStateElectionFilter(job);
        StateName afterStateElection = job.getState();
        this.backgroundJobServer.getStorageProvider().save(job);
        if (beforeStateElection != afterStateElection) {
            jobFilters.runOnStateAppliedFilters(job);
        }
    }

    private boolean isJobDeletedWhileProcessing(Exception e) {
        return hasCause(e, InterruptedException.class) && job.hasState(StateName.DELETED);
    }

    private boolean isJobServerStopped(Exception e) {
        return hasCause(e, InterruptedException.class) && !job.hasState(StateName.DELETED);
    }

    private boolean isMethodNotFoundException(Exception e) {
        return e instanceof JobNotFoundException;
    }
}
