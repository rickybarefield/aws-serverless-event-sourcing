package com.appagility.powercircles.model;

import com.amazonaws.auth.policy.Statement;
import com.appagility.powercircles.common.IamPolicyFunctions;
import com.appagility.powercircles.common.NamingContext;
import com.appagility.powercircles.networking.AwsNetwork;
import com.appagility.powercircles.networking.AwsSubnet;
import com.appagility.powercircles.wrappers.AwsRdsInstance;
import com.appagility.powercircles.wrappers.AwsRestApi;
import com.appagility.powercircles.wrappers.JavaLambda;
import com.appagility.powercircles.wrappers.RdsUser;
import com.pulumi.aws.ec2.SecurityGroup;
import com.pulumi.aws.ec2.SecurityGroupArgs;
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
import com.pulumi.aws.lambda.inputs.FunctionEnvironmentArgs;
import com.pulumi.aws.lambda.inputs.FunctionVpcConfigArgs;
import com.pulumi.aws.sns.Topic;
import com.pulumi.aws.sns.TopicSubscription;
import com.pulumi.aws.sns.TopicSubscriptionArgs;
import com.pulumi.aws.sqs.Queue;
import com.pulumi.aws.sqs.QueueArgs;
import com.pulumi.aws.sqs.QueuePolicy;
import com.pulumi.aws.sqs.QueuePolicyArgs;
import com.pulumi.core.Output;
import lombok.Builder;
import lombok.Singular;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.appagility.powercircles.common.ManagedPolicies.*;
import static com.appagility.powercircles.connectionfactories.RdsPostgresSecretAuthConnectionFactory.SECRET_NAME_ENV_VARIABLE;

@Builder
public class Projection {

    private String name;

    /**
     * Schema resource must be within the same package (directory) and JAR containing projectionHandler
     */
    private String schemaResourcePath;
    private Class<?> projectionHandler;
    @Singular("query")
    private List<Query> queries;
    private Class<?> queryHandler;
    private String projectionHandlerArtifactName;
    private String schemaName;

    public void defineInfrastructureAndSubscribeToEventBus(NamingContext parentNamingContext, Topic eventBus,
                                                           AwsRdsInstance projectionsInstance,
                                                           AwsNetwork awsNetwork,
                                                           List<AwsSubnet> dataSubnets, AwsRestApi restApi) {

        var namingContext = parentNamingContext.with(name);

        var projectionQueue = defineQueue(namingContext);
        subscribeQueueToEventBus(namingContext, projectionQueue, eventBus);
        defineSchema(namingContext, projectionsInstance);
        var rdsUser = projectionsInstance.createUserAndGrantAllPermissionsOnSchema(namingContext, name, schemaName);
        var projectionLambda = defineProjectionHandlerLambda(namingContext, projectionsInstance, rdsUser, awsNetwork, dataSubnets);
        connectQueueToLambda(namingContext, projectionQueue, projectionLambda);

        var queryHandlerLambda = defineQueryHandlerLambda(namingContext, projectionsInstance, rdsUser, awsNetwork, dataSubnets);

        queries.forEach(c -> c.defineRouteAndConnectToQueryHandler(namingContext, restApi, queryHandlerLambda));
    }

    private void defineSchema(NamingContext namingContext, AwsRdsInstance projectionsInstance) {

        try(var schemaResource = projectionHandler.getClassLoader().getResourceAsStream(schemaResourcePath)) {

            projectionsInstance.executeAsSql(namingContext, schemaResource);

        } catch (IOException e) {

            throw new RuntimeException(e);
        }
    }

    private Queue defineQueue(NamingContext namingContext) {

        return new Queue(namingContext.getName(), new QueueArgs.Builder()
                .fifoQueue(true)
                //TODO Temporary to avoid failures costing money, should look at DLQ instead
                .messageRetentionSeconds(60)
                .contentBasedDeduplication(true)
                .visibilityTimeoutSeconds((int) Aggregate.LAMBDA_TIMEOUT.toSeconds()).build());
    }

    private void subscribeQueueToEventBus(NamingContext namingContext, Queue projectionQueue, Topic eventBus) {

        defineQueuePolicyToAllowSnsToSend(namingContext, projectionQueue, eventBus);
        defineSubscription(namingContext, projectionQueue, eventBus);
    }

    private void defineQueuePolicyToAllowSnsToSend(NamingContext namingContext, Queue projectionQueue, Topic eventBus) {

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

        new QueuePolicy(namingContext.getName(), QueuePolicyArgs.builder()
                .policy(policyDocument.applyValue(GetPolicyDocumentResult::json))
                .queueUrl(projectionQueue.url())
                .build());
    }

    private void defineSubscription(NamingContext namingContext, Queue projectionQueue, Topic eventBus) {

        new TopicSubscription(namingContext.getName() + name, TopicSubscriptionArgs.builder()
                .endpoint(projectionQueue.arn())
                .topic(eventBus.arn())
                .protocol("sqs")
                .build());
    }


    private Function defineQueryHandlerLambda(NamingContext namingContext, AwsRdsInstance instance, RdsUser rdsUser, AwsNetwork awsNetwork, List<AwsSubnet> dataSubnets) {

        return defineLambda(namingContext, queryHandler, "query", instance, rdsUser, awsNetwork, dataSubnets);
    }


    private Function defineProjectionHandlerLambda(NamingContext namingContext, AwsRdsInstance instance, RdsUser rdsUser, AwsNetwork awsNetwork, List<AwsSubnet> dataSubnets) {

        return defineLambda(namingContext, projectionHandler, "projection", instance, rdsUser, awsNetwork, dataSubnets);
    }

    private Function defineLambda(NamingContext parentNamingContext, Class<?> handler, String handlerType, AwsRdsInstance instance, RdsUser rdsUser, AwsNetwork awsNetwork, List<AwsSubnet> dataSubnets) {

        var namingContext = parentNamingContext.with(handlerType);

        var subnetIds = Output.all(dataSubnets.stream().map(AwsSubnet::getId).toList());

        var vpcId = dataSubnets.get(0).getVpcId();

        var lambdaSecurityGroup = new SecurityGroup(namingContext.getName(), SecurityGroupArgs.builder()
                .vpcId(vpcId)
                .build());

        awsNetwork.allowAccessToSecretsManager(namingContext, lambdaSecurityGroup);

        instance.allowAccessFrom(namingContext, lambdaSecurityGroup);

        var securityGroupIds = lambdaSecurityGroup.id().applyValue(Collections::singletonList);

        var lambdaRole = defineRoleForLambda(namingContext, rdsUser);

        return JavaLambda.builder()
                .namingContext(namingContext)
                .artifactName(projectionHandlerArtifactName)
                .handler(handler)
                .role(lambdaRole)
                .environment(instance.getConnectionDetails(rdsUser).applyValue(
                        connectionDetails -> FunctionEnvironmentArgs.builder()
                                .variables(Map.of("DB_URL", connectionDetails.url(),
                                        "DB_USERNAME", connectionDetails.username(),
                                        "DB_PORT", connectionDetails.port(),
                                        SECRET_NAME_ENV_VARIABLE, connectionDetails.secretName()))
                                .build()))
                .vpcConfig(FunctionVpcConfigArgs.builder()
                        .subnetIds(subnetIds)
                        .securityGroupIds(securityGroupIds)
                        .build())
                .build()
                .define();

    }

    private Role defineRoleForLambda(NamingContext namingContext, RdsUser user) {

        var assumeLambdaRole = IamPolicyFunctions.createAssumeRolePolicyDocument("lambda.amazonaws.com");
        var allowConnectToDatabasePolicy = user.createPolicyToGetSecret(namingContext);

        return new Role(namingContext.getName(), new RoleArgs.Builder()
                .assumeRolePolicy(assumeLambdaRole.applyValue(GetPolicyDocumentResult::json))
                .managedPolicyArns(allowConnectToDatabasePolicy.arn().applyValue(rdsSecretPolicy ->
                                List.of(LambdaVpcAccessExecution.getArn(),
                                        LambdaSqaQueueExecution.getArn(),
                                        rdsSecretPolicy)))
                .build());
    }

    private void connectQueueToLambda(NamingContext namingContext, Queue projectionQueue, Function projectionLambda) {

        new EventSourceMapping(namingContext.with("subscription").getName(), EventSourceMappingArgs.builder()
                .batchSize(1)
                .eventSourceArn(projectionQueue.arn())
                .functionName(projectionLambda.arn())
                .build());
    }
}
