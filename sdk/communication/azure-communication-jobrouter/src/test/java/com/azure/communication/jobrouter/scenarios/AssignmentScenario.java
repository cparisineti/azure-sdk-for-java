package com.azure.communication.jobrouter.scenarios;

import com.azure.communication.jobrouter.JobRouterTestBase;
import com.azure.communication.jobrouter.RouterAdministrationClient;
import com.azure.communication.jobrouter.RouterAdministrationClientBuilder;
import com.azure.communication.jobrouter.RouterClient;
import com.azure.communication.jobrouter.RouterClientBuilder;
import com.azure.communication.jobrouter.models.AcceptJobOfferResult;
import com.azure.communication.jobrouter.models.ChannelConfiguration;
import com.azure.communication.jobrouter.models.DistributionPolicy;
import com.azure.communication.jobrouter.models.JobOffer;
import com.azure.communication.jobrouter.models.JobQueue;
import com.azure.communication.jobrouter.models.LabelValue;
import com.azure.communication.jobrouter.models.QueueAssignment;
import com.azure.communication.jobrouter.models.RouterJob;
import com.azure.communication.jobrouter.models.RouterWorker;
import com.azure.communication.jobrouter.models.options.CloseJobOptions;
import com.azure.communication.jobrouter.models.options.CreateJobOptions;
import com.azure.communication.jobrouter.models.options.CreateWorkerOptions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AssignmentScenario extends JobRouterTestBase {

    private RouterClient routerClient;

    private RouterAdministrationClient routerAdminClient;

    @Override
    protected void beforeTest() {
        routerClient = clientSetup(httpPipeline -> new RouterClientBuilder()
            .connectionString(getConnectionString())
            .pipeline(httpPipeline)
            .buildClient());

        routerAdminClient = clientSetup(httpPipeline -> new RouterAdministrationClientBuilder()
            .connectionString(getConnectionString())
            .pipeline(httpPipeline)
            .buildClient());
    }

    @Test
    public void assignmentScenario() {
        // // Setup
        String channelId = "assignmentScenarioChannel";

        // Create Distribution policy
        String distributionPolicyId = String.format("%s-AssignmentScenario-DistributionPolicy", JAVA_LIVE_TESTS);
        DistributionPolicy distributionPolicy = createDistributionPolicy(routerAdminClient, distributionPolicyId);

        // Create Queue
        String queueId = String.format("%s-AssignmentScenario-Queue", JAVA_LIVE_TESTS);
        JobQueue jobQueue = createQueue(routerAdminClient, queueId, distributionPolicy.getId());

        // CreateWorker
        String workerId = String.format("%s-AssignmentScenario-Worker", JAVA_LIVE_TESTS);

        Map<String, LabelValue> labels = new HashMap<String, LabelValue>() {
            {
                put("Label", new LabelValue("Value"));
            }
        };

        Map<String, Object> tags = new HashMap<String, Object>() {
            {
                put("Tag", "Value");
            }
        };

        ChannelConfiguration channelConfiguration = new ChannelConfiguration();
        channelConfiguration.setCapacityCostPerJob(1);
        Map<String, ChannelConfiguration> channelConfigurations = new HashMap<String, ChannelConfiguration>() {
            {
                put(channelId, channelConfiguration);
            }
        };

        Map<String, QueueAssignment> queueAssignments = new HashMap<String, QueueAssignment>() {
            {
                put(jobQueue.getId(), new QueueAssignment());
            }
        };

        CreateWorkerOptions createWorkerOptions = new CreateWorkerOptions(workerId, 10)
            .setLabels(labels)
            .setTags(tags)
            .setAvailableForOffers(false)
            .setChannelConfigurations(channelConfigurations)
            .setQueueAssignments(queueAssignments);

        routerClient.createWorker(createWorkerOptions);

        // Create job
        String jobId = String.format("%s-AssignmentScenario-RouterJob", JAVA_LIVE_TESTS);

        CreateJobOptions createJobOptions = new CreateJobOptions(jobId, channelId, queueId)
            .setPriority(1);
        RouterJob routerJob = routerClient.createJob(createJobOptions);

        // // Action, Verify
        RouterWorker polledWorker = routerClient.getWorker(workerId);

        assertTrue(polledWorker.getOffers().stream().anyMatch(x -> x.getJobId() == jobId));

        JobOffer jobOffer = polledWorker.getOffers().stream().filter(x -> x.getJobId() == jobId).findFirst().get();

        assertEquals(1, jobOffer.getCapacityCost());
        assertNotNull(jobOffer.getOfferTimeUtc());
        assertNotNull(jobOffer.getExpiryTimeUtc());

        AcceptJobOfferResult acceptJobOfferResult = routerClient.acceptJobOffer(polledWorker.getId(), jobOffer.getId());

        assertEquals(jobId, acceptJobOfferResult.getJobId());
        assertEquals(workerId, acceptJobOfferResult.getWorkerId());

        routerClient.completeJob(
            acceptJobOfferResult.getJobId(),
            acceptJobOfferResult.getAssignmentId(),
            String.format("Job completed by %s", workerId));

        CloseJobOptions closeJobOptions = new CloseJobOptions(acceptJobOfferResult.getJobId(), acceptJobOfferResult.getAssignmentId());
        routerClient.closeJob(closeJobOptions);

        // Cleanup
        routerClient.deleteJob(jobId);
        routerClient.deleteWorker(workerId);
        routerAdminClient.deleteQueue(queueId);
        routerAdminClient.deleteDistributionPolicy(distributionPolicyId);

    }
}
