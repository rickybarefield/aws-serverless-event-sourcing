package com.appagility.powercircles;

import com.amazonaws.auth.policy.Statement;
import com.pulumi.aws.iam.IamFunctions;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementConditionArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementPrincipalArgs;
import com.pulumi.aws.iam.outputs.GetPolicyDocumentResult;
import com.pulumi.aws.sns.Topic;
import com.pulumi.aws.sns.TopicSubscription;
import com.pulumi.aws.sns.TopicSubscriptionArgs;
import com.pulumi.aws.sqs.Queue;
import com.pulumi.aws.sqs.QueueArgs;
import com.pulumi.aws.sqs.QueuePolicy;
import com.pulumi.aws.sqs.QueuePolicyArgs;
import lombok.Builder;

import java.io.IOException;
import java.util.List;

@Builder
public class Projection {

    private String name;

    /**
     * Schema resource must be within JAR containing projectionHandler
     */
    private String schemaResourcePath;
    private Class<?> projectionHandler;
    private String projectionHandlerArtifactName;

    public void defineInfrastructureAndSubscribeToEventBus(Topic eventBus,
                                                           AwsRdsInstance projectionsInstance) {

        var projectionQueue = defineQueue();
        subscribeQueueToEventBus(projectionQueue, eventBus);

        defineSchema(projectionsInstance);

    }

    private void defineSchema(AwsRdsInstance projectionsInstance) {

        try(var schemaResource = projectionHandler.getClassLoader().getResourceAsStream(schemaResourcePath)) {

            projectionsInstance.exectuteAsSql(name + "-schema", schemaResource);

        } catch (IOException e) {

            throw new RuntimeException(e);
        }
    }

    private Queue defineQueue() {

        return new Queue(name + "-projection", new QueueArgs.Builder()
                .fifoQueue(true)
                .contentBasedDeduplication(true)
                .visibilityTimeoutSeconds((int) Aggregate.LAMBDA_TIMEOUT.toSeconds()).build());
    }

    private void subscribeQueueToEventBus(Queue projectionQueue, Topic eventBus) {

        defineQueuePolicyToAllowSnsToSend(projectionQueue, eventBus);
        defineSubscription(projectionQueue, eventBus);
    }

    private void defineQueuePolicyToAllowSnsToSend(Queue projectionQueue, Topic eventBus) {
        var policyDocument = IamFunctions.getPolicyDocument(GetPolicyDocumentArgs.builder()
                .statements(GetPolicyDocumentStatementArgs.builder()
                        .effect(Statement.Effect.Allow.name())
                        .actions("sqs:SendMessage")
                        .principals(GetPolicyDocumentStatementPrincipalArgs.builder()
                                .type("Service")
                                .identifiers("sns.amazonaws.com")
                                .build())
                        .resources(projectionQueue.arn().applyValue(List::of))
                        .conditions(GetPolicyDocumentStatementConditionArgs.builder()
                                .test("ArnEquals")
                                .variable("aws:SourceArn")
                                .values(eventBus.arn().applyValue(List::of))
                                .build())
                        .build())
                .build());

        new QueuePolicy("sns_to_projection_queue_" + name, QueuePolicyArgs.builder()
                .policy(policyDocument.applyValue(GetPolicyDocumentResult::json))
                .queueUrl(projectionQueue.url())
                .build());
    }

    private void defineSubscription(Queue projectionQueue, Topic eventBus) {

        new TopicSubscription("sns_to_projection_queue_" + name, TopicSubscriptionArgs.builder()
                .endpoint(projectionQueue.arn())
                .topic(eventBus.arn())
                .protocol("sqs")
                .build());
    }
}
