package de.otto.jobstore.web;

import de.otto.jobstore.common.*;
import de.otto.jobstore.service.JobInfoService;
import de.otto.jobstore.service.JobService;
import de.otto.jobstore.service.exception.*;
import de.otto.jobstore.web.representation.JobInfoRepresentation;
import de.otto.jobstore.web.representation.JobNameRepresentation;
import org.apache.abdera.Abdera;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.StringWriter;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Path("/jobs")
public class JobInfoResource {

    public static final String OTTO_JOBS_XML  = "application/vnd.otto.jobs+xml";
    public static final String OTTO_JOBS_JSON = "application/vnd.otto.jobs+json";

    public static final int MAX_LOG_LINES = 100;

    private final JobService jobService;

    private final JobInfoService jobInfoService;

    public JobInfoResource(JobService jobService, JobInfoService jobInfoService) {
        this.jobService = jobService;
        this.jobInfoService = jobInfoService;
    }

    /**
     * Returns an atom feed with the available job names. Each entry contains a link to retrieve all jobs for a given name
     *
     * @param uriInfo The uriInfo injected by Jax-RS
     * @return The atom feed with the available job names
     */
    @GET
    @Produces(MediaType.APPLICATION_ATOM_XML)
    public Response getJobs(@Context final UriInfo uriInfo) {
        return getJobs(new DefaultURIFactory(this.getClass(), uriInfo));
    };

    Response getJobs(URIFactory uriFactory) {
        final Abdera abdera = new Abdera();
        final Feed feed = createFeed(abdera, "Job Names", "A list of the available distinct job names", uriFactory.create());
        try {
            final JAXBContext ctx = JAXBContext.newInstance(JobNameRepresentation.class);
            final Marshaller marshaller = ctx.createMarshaller();
            for (String name : jobService.listJobNames()) {
                final URI uri = uriFactory.create(name);
                final StringWriter writer = new StringWriter();
                marshaller.marshal(new JobNameRepresentation(name), writer);
                final Entry entry = abdera.newEntry();
                entry.addLink(uri.getPath(), "self");
                entry.setContent(writer.toString(), OTTO_JOBS_XML);
                feed.addEntry(entry);
            }
        } catch (JAXBException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(feed).build();
    }

    /**
     * Enables job execution
     */
    @POST
    @Path("/enable")
    @Produces(MediaType.APPLICATION_JSON)
    public Response enableJobExecution() {
        final boolean executionEnabled = true;
        jobService.setExecutionEnabled(executionEnabled);
        return Response.ok(buildStatusJson(executionEnabled)).build();
    }

    /**
     * Enables job execution
     */
    @POST
    @Path("/disable")
    @Produces(MediaType.APPLICATION_JSON)
    public Response disableJobExecution() {
        final boolean executionEnabled = false;
        jobService.setExecutionEnabled(executionEnabled);
        return Response.ok(buildStatusJson(executionEnabled)).build();
    }

    /**
     * Executes a job and its content location. You can provide parameters as normal query parameters.
     * The service will fail if a query parameter has multiple or no values.
     *
     * @param name The name of the job to execute
     * @param uriInfo The uriInfo injected by Jax-RS
     * @return The content location of the job
     */
    @POST
    @Path("/{name}")
    public Response executeJob(@PathParam("name") final String name, @Context final UriInfo uriInfo) {
        return executeJob(name, new DefaultURIFactory(this.getClass(), uriInfo), new DefaultParameterExtractor(uriInfo));
    }

    Response executeJob(final String name, final URIFactory uriFactory, final ParameterExtractor extractor)  {
        Map<String, String> parameters;
        try {
            parameters = extractFirstParameters(extractor.getQueryParameters());
        } catch(IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
        try {
            final String jobId = jobService.executeJob(name, JobExecutionPriority.FORCE_EXECUTION, parameters);
            final URI uri = uriFactory.create(name, jobId);
            return Response.created(uri).build();
        } catch (JobNotRegisteredException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (JobExecutionNotNecessaryException | JobExecutionDisabledException e) {
            return Response.status(Response.Status.PRECONDITION_FAILED).entity(e.getMessage()).build();
        } catch (JobAlreadyQueuedException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        } catch (JobAlreadyRunningException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        } catch (JobServiceNotActiveException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    Map<String, String> extractFirstParameters(Map<String, List<String>> queryParameters) {
        Map<String, String> parameters = new HashMap<>();
        if(queryParameters == null) {
            return parameters;
        }
        for(String key : queryParameters.keySet()) {
            List<String> value = queryParameters.get(key);
            if (value == null || value.size() != 1) {
                throw new IllegalArgumentException("value for key '"+key+"' is ambiguous ("+value+")");
            }
            parameters.put(key, value.get(0));
        }
        return parameters;
    }

    /**
     * Returns an atom feed the latest jobs of the given name
     *
     * @param name The name of the jobs to return
     * @param size The number of jobs to return, default value is 10
     * @param uriInfo The uriInfo injected by Jax-RS
     * @return An atom with with the latest jobs
     */
    @GET
    @Path("/{name}")
    @Produces(MediaType.APPLICATION_ATOM_XML)
    public Response getJobsByName(@PathParam("name") final String name, @QueryParam("size") @DefaultValue("10") final int size,
                                  @Context final UriInfo uriInfo) {
        return getJobsByName(name, size, new DefaultURIFactory(this.getClass(), uriInfo));
    }

    Response getJobsByName(final String name, final int size, final URIFactory uriFactory) {
        final Abdera abdera = new Abdera();
        final Feed feed = createFeed(
                abdera,
                "JobInfo Objects", "A list of the " + size + " most recent jobInfo objects with name " + name,
                uriFactory.create(name));
        try {
            final JAXBContext ctx = JAXBContext.newInstance(JobInfoRepresentation.class);
            final Marshaller marshaller = ctx.createMarshaller();
            for (JobInfo jobInfo : jobInfoService.getByName(name, size)) {
                final URI uri = uriFactory.create(name, jobInfo.getId());
                final StringWriter writer = new StringWriter();
                marshaller.marshal(JobInfoRepresentation.fromJobInfo(jobInfo, MAX_LOG_LINES), writer);
                final Entry entry = abdera.newEntry();
                entry.addLink(uri.getPath(), "self");
                entry.setContent(writer.toString(), OTTO_JOBS_XML);
                feed.addEntry(entry);
            }
        } catch (JAXBException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(feed).build();
    }

    /**
     * Enables execution of jobs with the given name
     * @param name The name of the job to enable/disable
     * @return The current status of the status (enabled true/false)
     */
    @POST
    @Path("/{name}/enable")
    public Response enableJob(@PathParam("name") final String name) {
        try {
            final boolean executionEnabled = true;
            jobService.setJobExecutionEnabled(name, executionEnabled);
            return Response.ok(buildStatusJson(executionEnabled)).build();
        } catch (JobNotRegisteredException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    /**
     * Disables execution of jobs with the given name
     * @param name The name of the job to enable/disable
     * @return The current status of the status (enabled true/false)
     */
    @POST
    @Path("/{name}/disable")
    public Response disableJob(@PathParam("name") final String name) {
        try {
            final boolean executionEnabled = false;
            jobService.setJobExecutionEnabled(name, executionEnabled);
            return Response.ok(buildStatusJson(executionEnabled)).build();
        } catch (JobNotRegisteredException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("/status")
    public Response statusOfAllJobs() {
        boolean executionEnabled = jobService.isExecutionEnabled();
        return Response.ok(buildStatusJson(executionEnabled)).build();
    }

    /**
     * Returns the job with the given name and id
     *
     * @param name The name of the job to return
     * @param id The id of the job to return
     * @return The job
     */
    @GET
    @Path("/{name}/{id}")
    @Produces({ OTTO_JOBS_JSON, OTTO_JOBS_XML})
    public Response getJob(@PathParam("name") final String name, @PathParam("id") final String id) {
        final JobInfo jobInfo = jobInfoService.getById(id);
        if (jobInfo == null || !jobInfo.getName().equals(name)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } else {
            return Response.ok(JobInfoRepresentation.fromJobInfo(jobInfo, MAX_LOG_LINES)).build();
        }
    }

    /**
     * Aborts the execution of a job if it supports this.
     *
     * @param name The name of the job
     * @param id The id of the job
     */
    @POST
    @Path("/{name}/{id}/abort")
    public Response abortJob(@PathParam("name") final String name, @PathParam("id") final String id) {
        final JobInfo jobInfo = jobInfoService.getById(id);
        if (jobInfo == null || !jobInfo.getName().equals(name)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } else {
            if (isJobAbortable(jobInfo)) {
                jobService.abortJob(jobInfo.getId());
                return Response.ok().build();
            } else {
                return Response.status(Response.Status.FORBIDDEN).entity("Job does not support being aborted.").build();
            }
        }
    }

    private boolean isJobAbortable(JobInfo jobInfo) {
        JobDefinition jobDefinition = null;
        for (JobRunnable jobRunnable : jobService.listJobRunnables()) {
            if (jobInfo.getName().equals(jobRunnable.getJobDefinition().getName())) {
                jobDefinition = jobRunnable.getJobDefinition();
                break;
            }
        }
        return jobDefinition != null && jobDefinition.isAbortable();
    }


    /**
     * <b>INTERNAL API, DO NOT USE</b>
     * Returns a map with the distinct job names as the key and the jobs with the given name as their values.
     * Without given jobNames, jobs will NOT be returned. We need to rewrite the interface.
     *
     * @param hours The hours the jobs go back into the past
     * @param resultCodes Filter the jobs by their result status (default null == unfiltered)
     * @param jobNames Filter the jobs by their name (default null == all jobs, but without jobs itself)
     * @return The map of distinct names with their jobs as values
     */
    @GET
    @Path("/history")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getJobsHistory(@QueryParam("hours") @DefaultValue("12") final int hours,
                                   @QueryParam("resultCode") final Set<ResultCode> resultCodes,
                                   @QueryParam("jobName") final Set<String> jobNames) {
        final Collection<String> allJobNames = jobService.listJobNames();
        final Date dt = new Date(new Date().getTime() - TimeUnit.HOURS.toMillis(hours));

        final Map<String, List<JobInfoRepresentation>> jobs = new LinkedHashMap<>();
        for (String jobName : allJobNames) {
            final List<JobInfoRepresentation> jobInfoRepresentations = new ArrayList<>();

            if (jobNames == null || jobNames.isEmpty()) {
                // without jobNames we return a list with empty result, values must be get after first call
                jobs.put(jobName, jobInfoRepresentations);
            } else {
                // otherwise only add if in list of jobs
                if (jobNames.contains(jobName)) {
                    final List<JobInfo> jobInfoList = jobInfoService.getByNameAndTimeRange(jobName, dt, new Date(), resultCodes);

                    for (JobInfo jobInfo : jobInfoList) {
                        jobInfoRepresentations.add(JobInfoRepresentation.fromJobInfo(jobInfo, MAX_LOG_LINES));
                    }
                    jobs.put(jobName, jobInfoRepresentations);
                }
            }
        }
        return Response.ok(jobs).build();
    }

    private String buildStatusJson(boolean newStatus) {
        Collection<String> jobs = jobService.listJobNames();

        List<String> localRunningJobs = new ArrayList<>();
        for (String job : jobs) {
            if (isRunningAndNotRemote(job)) {
                localRunningJobs.add(job);
                break;
            }
        }

        return "{"+
                "  \"status\" : " + (newStatus ? "\"enabled\"" : "\"disabled\"") +
                "  ," +
                "  \"localRunningJobs\" : " + !localRunningJobs.isEmpty() +
                "}";
    }

    private boolean isRunningAndNotRemote(String job) {
        JobInfo jobInfo = jobInfoService.getByNameAndRunningState(job, RunningState.RUNNING);
        return jobInfo != null && !jobService.getJobDefinitionByName(job).isRemote();
    }

    private Feed createFeed(final Abdera abdera, String title, final String subTitle, final URI feedLink) {
        final Feed feed = abdera.newFeed();
        feed.setId("urn:uuid:" + UUID.randomUUID().toString());
        feed.setTitle(title);
        feed.setSubtitle(subTitle);
        feed.setUpdated(new Date());
        feed.addLink(feedLink.getPath(),"self");
        return feed;
    }

}
