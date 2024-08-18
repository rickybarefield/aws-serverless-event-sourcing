package com.appagility.powercircles;

import com.amazonaws.auth.policy.Statement;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.StreamViewType;
import com.pulumi.asset.AssetArchive;
import com.pulumi.asset.FileArchive;
import com.pulumi.asset.StringAsset;
import com.pulumi.aws.apigateway.RestApi;
import com.pulumi.aws.dynamodb.Table;
import com.pulumi.aws.dynamodb.TableArgs;
import com.pulumi.aws.dynamodb.inputs.TableAttributeArgs;
import com.pulumi.aws.iam.IamFunctions;
import com.pulumi.aws.iam.Role;
import com.pulumi.aws.iam.RoleArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementArgs;
import com.pulumi.aws.iam.outputs.GetPolicyDocumentResult;
import com.pulumi.aws.lambda.*;
import com.pulumi.aws.lambda.enums.Runtime;
import com.pulumi.aws.lambda.inputs.FunctionEnvironmentArgs;
import com.pulumi.aws.sns.Topic;
import com.pulumi.aws.sns.TopicArgs;
import com.pulumi.core.Output;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Builder
public class Aggregate {

    public static final Duration LAMBDA_TIMEOUT = Duration.ofMinutes(1);
    public static final String PROJECTION_DATABASE_USERNAME = "sa";
    private String name;

    @Singular
    @Getter
    private List<Command> commands;

    @Singular
    private List<Projection> projections;

    private String commandHandlerArtifactName;
    private String commandHandlerName;

    public void defineInfrastructure(RestApi restApi, AwsNetwork awsNetwork, List<AwsSubnet> dataSubnets) {

        var eventStore = defineDynamoTable();

        var commandHandler = defineLambdaForCommandHandler(eventStore, restApi);

        var eventBus = defineTopicForEventBus();

        defineStreamFromEventStoreToEventBus(eventStore, eventBus);

        var rdsInstance = AwsRdsInstance.builder()
                .awsNetwork(awsNetwork)
                .awsSubnets(dataSubnets)
                .name(name)
                .username(PROJECTION_DATABASE_USERNAME)
                .build();

        rdsInstance.defineInfrastructure();

        commands.forEach(c -> c.defineRouteAndConnectToCommandBus(restApi, commandHandler));
        projections.forEach(p -> p.defineInfrastructureAndSubscribeToEventBus(eventBus, rdsInstance));
    }

    private Topic defineTopicForEventBus() {

        return new Topic(name + "-events", TopicArgs.builder()
                .fifoTopic(true)
                .build());
    }

    private void defineStreamFromEventStoreToEventBus(Table eventStore, Topic eventBus) {


        var allowWriteToSnsTopic = IamFunctions.getPolicyDocument(GetPolicyDocumentArgs.builder()
                .statements(eventBus.arn().applyValue(eventBusArn -> Collections.singletonList(GetPolicyDocumentStatementArgs.builder()
                        .effect(Statement.Effect.Allow.name())
                        .actions("sns:Publish")
                        .resources(eventBusArn)
                        .build())))
                .build());

        var allowReadFromStream = IamFunctions.getPolicyDocument(GetPolicyDocumentArgs.builder()
                .statements(eventStore.streamArn().applyValue(eventStoreStreamArn -> Collections.singletonList(GetPolicyDocumentStatementArgs.builder()
                        .effect(Statement.Effect.Allow.name())
                        .actions("dynamodb:GetRecords", "dynamodb:GetShardIterator", "dynamodb:DescribeStream", "dynamodb:ListStreams")
                        .resources(eventStoreStreamArn)
                        .build())))
                .build());

        var allowWriteToSnsForLambdaPolicy = IamPolicyFunctions.policyForDocument(allowWriteToSnsTopic,
                "allow_write_to_sns");

        var allowReadFromStreamPolicy = IamPolicyFunctions.policyForDocument(allowReadFromStream,
                "allow_read_from_dynamo_stream");

        var assumeLambdaRole = IamPolicyFunctions.createAssumeRolePolicyDocument("lambda.amazonaws.com");

        var role = new Role("forwarder-lambda-role", new RoleArgs.Builder()
                .assumeRolePolicy(assumeLambdaRole.applyValue(GetPolicyDocumentResult::json))
                .managedPolicyArns(Output.all(allowWriteToSnsForLambdaPolicy.arn(), allowReadFromStreamPolicy.arn()).applyValue(policyArns -> List.of(
                        "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole",
                        policyArns.get(0), policyArns.get(1))))
                .build());

        readForwarderLambdaCode();

        var code = readForwarderLambdaCode();

        var function = new Function(name + "-forwarder-for-events", FunctionArgs
                .builder()
                .runtime(Runtime.NodeJS20dX)
                .handler("index.handler")
                .role(role.arn())
                .code(new AssetArchive(Map.of("index.js", new StringAsset(code))))
                .timeout((int) LAMBDA_TIMEOUT.toSeconds())
                .environment(eventBus.arn().applyValue(
                        eventBusArn -> FunctionEnvironmentArgs.builder()
                                .variables(Map.of("TARGET_TOPIC_ARN", eventBusArn)).build()))
                .build());

        new EventSourceMapping(name, EventSourceMappingArgs.builder()
                .eventSourceArn(eventStore.streamArn())
                .functionName(function.name())
                .startingPosition("LATEST")
                .batchSize(1).build());
    }

    private String readForwarderLambdaCode() {

        try (var codeResource = Aggregate.class.getClassLoader().getResourceAsStream("stream_forwarder.js")) {
            return new String(codeResource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private Table defineDynamoTable() {

        return new Table(name, new TableArgs.Builder()
                .attributes(
                        new TableAttributeArgs.Builder().name("id").type("S").build(),
                        new TableAttributeArgs.Builder().name("sequenceNumber").type("N").build()
                )
                .hashKey("id")
                .rangeKey("sequenceNumber")
                .billingMode(BillingMode.PAY_PER_REQUEST.name())
                .streamEnabled(true)
                .streamViewType(StreamViewType.NEW_IMAGE.name())
                .build());
    }

    private Function defineLambdaForCommandHandler(Table eventStore, RestApi restApi) {

        var lambdaRole = defineRoleForLambda(eventStore);

        var function = new Function(name, FunctionArgs
                .builder()
                .runtime(Runtime.Java21)
                .handler(commandHandlerName)
                .role(lambdaRole.arn())
                .code(new FileArchive("./target/lambdas/" + commandHandlerArtifactName))
                .timeout((int) LAMBDA_TIMEOUT.toSeconds())
                .environment(eventStore.name().applyValue(
                        tableName -> FunctionEnvironmentArgs.builder()
                                .variables(Map.of("PERSON_TABLE_NAME", tableName)).build()))
                .build());

        new Permission("allow_invoke_from_restapi", PermissionArgs.builder()
                .action("lambda:InvokeFunction")
                .function(function.name())
                .principal("apigateway.amazonaws.com")
                .sourceArn(restApi.executionArn().applyValue(arn -> arn + "/*/*"))
                .build());

        return function;
    }

    private static Role defineRoleForLambda(Table table) {

        var allowAllOnDynamoForTablePolicyDocument = IamFunctions.getPolicyDocument(GetPolicyDocumentArgs.builder()
                .statements(table.arn().applyValue(tableArn -> Collections.singletonList(GetPolicyDocumentStatementArgs.builder()
                        .effect(Statement.Effect.Allow.name())
                        .actions("dynamodb:*")
                        .resources(tableArn)
                        .build())))
                .build());

        var allowAllOnDynamoForTablePolicy = IamPolicyFunctions.policyForDocument(allowAllOnDynamoForTablePolicyDocument,
                "allow_all_on_dynamodb");

        var assumeLambdaRole = IamPolicyFunctions.createAssumeRolePolicyDocument("lambda.amazonaws.com");

        return new Role("lambda-role", new RoleArgs.Builder()
                .assumeRolePolicy(assumeLambdaRole.applyValue(GetPolicyDocumentResult::json))
                .managedPolicyArns(allowAllOnDynamoForTablePolicy.arn().applyValue(dynamoArn -> List.of(
                        "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole",
                        dynamoArn)))
                .build());
    }

}
