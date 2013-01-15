package de.otto.jobstore.service;

import de.otto.jobstore.common.*;
import de.otto.jobstore.common.properties.JobInfoProperty;
import de.otto.jobstore.repository.JobInfoRepository;
import de.otto.jobstore.service.exception.JobException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.annotation.Resource;
import java.net.URI;
import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.*;

@ContextConfiguration(locations = {"classpath:spring/lhotse-jobs-context.xml"})
public class JobServiceIntegrationTest extends AbstractTestNGSpringContextTests {

    @Resource(name = "jobServiceWithoutRemoteJobExecutorService")
    private JobService jobService;

    @Resource
    private JobInfoRepository jobInfoRepository;

    @Resource
    private JobInfoService jobInfoService;

    private RemoteJobExecutorService remoteJobExecutorService = mock(RemoteJobExecutorService.class);
    private RemoteJobRunnableMock jobRunnable;

    private static final String JOB_NAME_1 = "test_job_1";
    private static final String JOB_NAME_2 = "test_job_2";
    private static final String JOB_NAME_3 = "test_job_3";
    private static final Map<String, String> PARAMETERS = Collections.singletonMap("paramK", "paramV");
    private static final URI REMOTE_JOB_URI = URI.create("http://www.example.com");

    @BeforeMethod
    public void setUp() throws Exception {
        jobService.clean();
        jobInfoRepository.clear(true);
        reset(remoteJobExecutorService);
    }

    @Test
    public void testExecutingRemoteJob() throws Exception {
        jobRunnable = new RemoteJobRunnableMock(remoteJobExecutorService, jobInfoService, 0, 0);
        jobService.registerJob(jobRunnable);
        reset(remoteJobExecutorService);
        when(remoteJobExecutorService.startJob(any(RemoteJob.class))).thenReturn(REMOTE_JOB_URI);
        String id = jobService.executeJob(JOB_NAME_3);
        Thread.sleep(1000);
        // job should be started
        assertNotNull(id);
        JobInfo jobInfo = jobInfoRepository.findById(id);
        // job should still be running
        assertEquals(RunningState.RUNNING, RunningState.valueOf(jobInfo.getRunningState()));
        assertEquals(PARAMETERS, jobInfo.getParameters());
        assertEquals(REMOTE_JOB_URI.toString(), jobInfo.getAdditionalData().get(JobInfoProperty.REMOTE_JOB_URI.val()));

        // testPollingRunningRemoteJob
        reset(remoteJobExecutorService);
        List<String> logLines = new ArrayList<>();
        Collections.addAll(logLines, "log l.1", "log l.2");
        when(remoteJobExecutorService.getStatus(any(URI.class))).thenReturn(new RemoteJobStatus(RemoteJobStatus.Status.RUNNING, logLines, "bar"));
        // verify(remoteJobExecutorService, times(1)).

        jobService.pollRemoteJobs();

        jobInfo = jobInfoRepository.findByNameAndRunningState(JOB_NAME_3, RunningState.RUNNING);
        assertEquals(RunningState.RUNNING, RunningState.valueOf(jobInfo.getRunningState()));
        assertEquals(REMOTE_JOB_URI.toString(), jobInfo.getAdditionalData().get(JobInfoProperty.REMOTE_JOB_URI.val()));
        assertEquals(2, jobInfo.getLogLines().size());
        assertEquals("bar", jobInfo.getStatusMessage());

        //testPollingFinishedRemoteJob
        reset(remoteJobExecutorService);
        when(remoteJobExecutorService.getStatus(any(URI.class))).thenReturn(
                new RemoteJobStatus(RemoteJobStatus.Status.FINISHED, logLines, new RemoteJobResult(true, 0, "done"), "date"));

        jobService.pollRemoteJobs();

        jobInfo = jobInfoRepository.findByName(JOB_NAME_3, 1).get(0);
        assertTrue(jobInfo.getRunningState().startsWith("FINISHED"));
        assertEquals(REMOTE_JOB_URI.toString(), jobInfo.getAdditionalData().get(JobInfoProperty.REMOTE_JOB_URI.val()));
        assertEquals(ResultCode.SUCCESSFUL, jobInfo.getResultState());
        assertEquals(2, jobInfo.getLogLines().size());
        assertEquals("done", jobInfo.getResultMessage());
    }

    @Test
    public void testExecuteJobWhichViolatesRunningConstraints() throws Exception {
        jobService.registerJob(new LocalJobRunnableMock(JOB_NAME_1));
        jobService.registerJob(new LocalJobRunnableMock(JOB_NAME_2));
        Set<String> constraint = new HashSet<>();
        constraint.add(JOB_NAME_1); constraint.add(JOB_NAME_2);
        jobService.addRunningConstraint(constraint);

        String id1 = jobService.executeJob(JOB_NAME_1);
        String id2 = jobService.executeJob(JOB_NAME_2);

        JobInfo jobInfo1 = jobInfoRepository.findById(id1);
        assertNotNull(jobInfo1);
        assertEquals(RunningState.RUNNING.name(), jobInfo1.getRunningState());

        JobInfo jobInfo2 = jobInfoRepository.findById(id2);
        assertNotNull(jobInfo2);
        assertEquals(RunningState.QUEUED.name(), jobInfo2.getRunningState());


    }

    class LocalJobRunnableMock extends AbstractLocalJobRunnable {

        private final String name;

        LocalJobRunnableMock(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public long getMaxExecutionTime() {
            return 1000;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void execute(JobExecutionContext context) throws JobException {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }

    class RemoteJobRunnableMock extends AbstractRemoteJobRunnable {

        final long maxExecutionTime;
        final long pollingInterval;

        // TODO: (almost) same as in JobServiceTest
        RemoteJobRunnableMock(RemoteJobExecutorService remoteJobExecutorService, JobInfoService jobInfoService,  long maxExecutionTime, long pollingInterval) {
            super(remoteJobExecutorService, jobInfoService);
            this.maxExecutionTime = maxExecutionTime;
            this.pollingInterval = pollingInterval;
        }

        @Override
        public String getName() {
            return JOB_NAME_3;
        }

        @Override
        public Map<String, String> getParameters() {
            return PARAMETERS;
        }

        @Override
        public long getMaxExecutionTime() {
            return maxExecutionTime;
        }

        @Override
        public long getPollingInterval() {
            return pollingInterval;
        }
    }
}