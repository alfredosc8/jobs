package de.otto.jobstore.common;

import de.otto.jobstore.common.properties.JobInfoProperty;
import de.otto.jobstore.service.JobInfoService;
import de.otto.jobstore.service.RemoteJobExecutor;
import de.otto.jobstore.service.exception.JobException;
import de.otto.jobstore.service.exception.RemoteJobAlreadyRunningException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public abstract class AbstractRemoteJobRunnable implements JobRunnable {

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    protected final RemoteJobExecutor remoteJobExecutorService;
    protected final JobInfoService jobInfoService;

    protected AbstractRemoteJobRunnable(RemoteJobExecutor remoteJobExecutorService, JobInfoService jobInfoService) {
        this.remoteJobExecutorService = remoteJobExecutorService;
        this.jobInfoService = jobInfoService;
    }

    @Override
    public RemoteJobStatus getRemoteStatus(JobExecutionContext context) {
        final String remoteJobUri = context.getJobLogger().getAdditionalData(JobInfoProperty.REMOTE_JOB_URI.val());
        final RemoteJobStatus status = remoteJobExecutorService.getStatus(URI.create(remoteJobUri));
        final JobInfo jobInfo = jobInfoService.getById(context.getId());

        if (jobInfo != null && status.logLines != null
                && jobInfo.getLogLines() != null && !jobInfo.getLogLines().isEmpty()) {
            final int currentLength = jobInfo.getLogLines().size();
            // Assume that old lines are already included, and therefore can be cut off
            if (currentLength <= status.logLines.size()) {
                status.logLines = status.logLines.subList(currentLength, status.logLines.size());
            }
        }
        return status;
    }

    /**
     * By default returns true. If an exception occurs, returns false.
     */
    @Override
    public boolean prepare(JobExecutionContext context) {
        try {
            return doPrepare(context);
        } catch (Exception e) {
            return onException(context, e, State.PREPARE).hasRecovered();
        }
    }

    /**
     * Template method of de.otto.jobstore.common.AbstractRemoteJobRunnable#prepare(de.otto.jobstore.common.JobExecutionContext)
     */
    protected boolean doPrepare(JobExecutionContext context) throws JobException {
        return true;
    }

    /**
     * Only triggers the remote job, poll to check wether job is finished or not.
     *
     * @see de.otto.jobstore.service.JobService#pollRemoteJobs()
     */
    @Override
    public void execute(JobExecutionContext context) throws JobException {
        try {
            doExecute(context);
        } catch (Exception e) {
            onException(context, e, State.EXECUTE).doThrow();
        }
    }

    /**
     * Template method for de.otto.jobstore.common.AbstractRemoteJobRunnable#execute(de.otto.jobstore.common.JobExecutionContext)
     */
    protected void doExecute(JobExecutionContext context) throws JobException {
        final JobLogger jobLogger = context.getJobLogger();
        try {
            log.info("ltag={}.execute Trigger remote job jobName={} jobId={} ...",
                    this.getClass().getSimpleName(), getJobDefinition().getName(), context.getId());
            final JobInfo jobInfo = jobInfoService.getById(context.getId());
            final URI uri = remoteJobExecutorService.startJob(new RemoteJob(getJobDefinition().getName(), context.getId(), jobInfo.getParameters()));
            jobLogger.insertOrUpdateAdditionalData(JobInfoProperty.REMOTE_JOB_URI.val(), uri.toString());
        } catch (RemoteJobAlreadyRunningException e) {
            log.info("ltag={}.execute Remote job jobName={} jobId={} is already running: {}",
                    this.getClass().getSimpleName(), getJobDefinition().getName(), context.getId(), e.getMessage());
            jobLogger.insertOrUpdateAdditionalData("resumedAlreadyRunningJob", e.getJobUri().toString());
            jobLogger.insertOrUpdateAdditionalData(JobInfoProperty.REMOTE_JOB_URI.val(), e.getJobUri().toString());
        }
    }

    /**
     * Implementation might want to set the {@link JobExecutionContext#resultCode}
     */
    @Override
    public void afterExecution(JobExecutionContext context) throws JobException {
        try {
            doAfterExecution(context);
        } catch (Exception e) {
            onException(context, e, State.AFTER_EXECUTION).doThrow();
        }
    }

    /**
     * Template method for de.otto.jobstore.common.AbstractRemoteJobRunnable#afterExecution(de.otto.jobstore.common.JobExecutionContext)
     */
    protected void doAfterExecution(JobExecutionContext context) throws JobException {
    }

    @Override
    public OnException onException(JobExecutionContext context, final Exception e, State state) {
        return new DefaultOnException(e);
    }
}
