package de.otto.jobstore.common;

import de.otto.jobstore.service.exception.JobException;

public class DefaultOnException implements JobRunnable.OnException {
    private final Exception e;

    public DefaultOnException(Exception e) {
        this.e = e;
    }

    @Override
    public void doThrow() throws JobException {
        if (e instanceof JobException) {
            throw (JobException) e;
        }
        throw new JobException("Unexpected exception.", e){};
    }

    @Override
    public boolean hasRecovered() {
        return false;
    }
}
