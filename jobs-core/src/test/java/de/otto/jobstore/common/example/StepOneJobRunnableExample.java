package de.otto.jobstore.common.example;

import de.otto.jobstore.common.*;
import de.otto.jobstore.service.JobService;
import de.otto.jobstore.service.exception.JobException;
import de.otto.jobstore.service.exception.JobExecutionException;

import java.util.Map;

public final class StepOneJobRunnableExample extends AbstractLocalJobRunnable {

    private final JobService jobService;

    public StepOneJobRunnableExample(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public JobDefinition getJobDefinition() {
        return new AbstractLocalJobDefinition() {
            @Override
            public String getName() {
                return "STEP_ONE_JOB";
            }

            @Override
            public long getMaxExecutionTime() {
                return 1000 * 60 * 10;
            }

            @Override
            public long getMaxIdleTime() {
                return 1000 * 60 * 10;
            }
        };
    }

    /**
     * A very lazy job which triggers job two if done
     */
    @Override
    public void execute(JobExecutionContext executionContext) throws JobException {
        if (JobExecutionPriority.CHECK_PRECONDITIONS.equals(executionContext.getExecutionPriority())
                || jobService.listJobNames().contains(StepTwoJobRunnableExample.STEP_TWO_JOB)) {
            executionContext.setResultCode(ResultCode.FAILED);
        }
        try {
            for (int i = 0; i < 10; i++) {
                Thread.sleep(i * 1000);
            }
        } catch (InterruptedException e) {
            throw new JobExecutionException("Interrupted: " + e.getMessage());
        }
        jobService.executeJob(StepTwoJobRunnableExample.STEP_TWO_JOB);
        executionContext.setResultCode(ResultCode.SUCCESSFUL);
    }

}
