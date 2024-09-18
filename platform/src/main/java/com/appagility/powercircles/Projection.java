package com.appagility.powercircles;

import com.amazonaws.auth.policy.Statement;
import com.pulumi.aws.iam.IamFunctions;
import com.pulumi.aws.iam.Role;
import com.pulumi.aws.iam.RoleArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementConditionArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementPrincipalArgs;
import com.pulumi.aws.iam.outputs.GetPolicyDocumentResult;
import com.pulumi.aws.lambda.EventSourceMapping;
import com.pulumi.aws.lambda.EventSourceMappingArgs;
import com.pulumi.aws.lambda.Function;
import com.pulumi.aws.sns.Topic;
import com.pulumi.aws.sns.TopicSubscription;
import com.pulumi.aws.sns.TopicSubscriptionArgs;
import com.pulumi.aws.sqs.Queue;
import com.pulumi.aws.sqs.QueueArgs;
import com.pulumi.aws.sqs.QueuePolicy;
import com.pulumi.aws.sqs.QueuePolicyArgs;
import lombok.Builder;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.appagility.powercircles.ManagedPolicies.LambdaBasicExecution;
import static com.appagility.powercircles.ManagedPolicies.LambdaSqaQueueExection;

@Builder
public class Projection {

    private String name;

    /**
     * Schema resource must be within the same package (directory) and JAR containing projectionHandler
     */
    private String schemaResourcePath;
    private Class<?> projectionHandler;
    private String projectionHandlerArtifactName;

    public void defineInfrastructureAndSubscribeToEventBus(Topic eventBus,
                                                           AwsRdsInstance projectionsInstance) {

        var projectionQueue = defineQueue();
        subscribeQueueToEventBus(projectionQueue, eventBus);
        defineSchema(projectionsInstance);
        var projectionLambda = defineProjectionHandlerLambda();

        connectQueueToLambda(projectionQueue, projectionLambda);
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
                //TODO Temporary to avoid failures costing money, should look at DLQ instead
                .messageRetentionSeconds(60)
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

    private Function defineProjectionHandlerLambda() {

        var lambdaRole = defineRoleForLambda();

        return JavaLambda.builder()
                .name(name)
                .artifactName(projectionHandlerArtifactName)
                .handler(projectionHandler)
                .role(lambdaRole)
                .build()
                .define();
    }

    private Role defineRoleForLambda() {

        var assumeLambdaRole = IamPolicyFunctions.createAssumeRolePolicyDocument("lambda.amazonaws.com");

        return new Role("lambda-role-" + name, new RoleArgs.Builder()
                .assumeRolePolicy(assumeLambdaRole.applyValue(GetPolicyDocumentResult::json))
                .managedPolicyArns(List.of(LambdaBasicExecution.getArn(), LambdaSqaQueueExection.getArn()))
                .build());
    }

    private void connectQueueToLambda(Queue projectionQueue, Function projectionLambda) {

        new EventSourceMapping(name + "-subscription", EventSourceMappingArgs.builder()
                .batchSize(1)
                .eventSourceArn(projectionQueue.arn())
                .functionName(projectionLambda.arn())
                .build());
    }
}
