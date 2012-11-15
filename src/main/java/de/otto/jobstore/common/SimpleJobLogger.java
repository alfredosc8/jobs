package de.otto.jobstore.common;

import de.otto.jobstore.repository.api.JobInfoRepository;

public final class SimpleJobLogger implements JobLogger {

    private final String jobName;

    private final JobInfoRepository jobInfoRepository;

    public SimpleJobLogger(String jobName, JobInfoRepository jobInfoRepository) {
        this.jobName = jobName;
        this.jobInfoRepository = jobInfoRepository;
    }

    @Override
    public void addLoggingData(String log) {
        jobInfoRepository.addLoggingData(jobName, log);
    }

    @Override
    public void insertOrUpdateAdditionalData(String key, String value) {
        jobInfoRepository.insertAdditionalData(jobName, key, value);
    }

}
