package com.appagility.powercircles;

import com.amazonaws.auth.policy.Statement;
import com.pulumi.asset.FileArchive;
import com.pulumi.aws.apigatewayv2.Api;
import com.pulumi.aws.cloudwatch.*;
import com.pulumi.aws.cloudwatch.inputs.EventTargetSqsTargetArgs;
import com.pulumi.aws.dynamodb.Table;
import com.pulumi.aws.dynamodb.TableArgs;
import com.pulumi.aws.dynamodb.inputs.TableAttributeArgs;
import com.pulumi.aws.iam.*;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementConditionArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementPrincipalArgs;
import com.pulumi.aws.iam.outputs.GetPolicyDocumentResult;
import com.pulumi.aws.lambda.*;
import com.pulumi.aws.lambda.inputs.FunctionEnvironmentArgs;
import com.pulumi.aws.sqs.Queue;
import com.pulumi.aws.sqs.QueueArgs;
import com.pulumi.aws.sqs.QueuePolicy;
import com.pulumi.aws.sqs.QueuePolicyArgs;
import com.pulumi.core.Output;
import lombok.Builder;
import lombok.Singular;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Builder
public class Aggregate {

    public static final Duration LAMBDA_TIMEOUT = Duration.ofMinutes(1);
    private String name;

    @Singular
    private List<Command> commands;
    private String commandHandlerArtifactName;
    private String commandHandlerName;

    public void defineInfrastructure(Api apiGateway) {

        var table = defineDynamoTable();

        defineEventBus();

        var commandBus = defineCommandBus();
        var commandHandlerQueue = defineQueueForCommandHandler();

        var lambdaRole = defineRoleForLambda(table);
        var commandHandler = defineLambda(table, lambdaRole);

        defineConnectionBetween(commandBus, commandHandlerQueue);
        defineConnectionBetween(commandHandlerQueue, commandHandler);

        definePolicyAndAttachmentToReceiveFromSqs(commandHandlerQueue, lambdaRole);

        var roleForGatewayToConnectToBus = defineRoleForGatewayToConnectToCommandBus();

        commands.forEach(c -> c.defineRouteAndConnectToCommandBus(
                apiGateway,
                roleForGatewayToConnectToBus,
                commandBus));
    }


    private Table defineDynamoTable() {

        return new Table(name, new TableArgs.Builder()
                .attributes(
                        new TableAttributeArgs.Builder().name("id").type("S").build(),
                        new TableAttributeArgs.Builder().name("sequenceNumber").type("N").build()
                )
                .hashKey("id")
                .rangeKey("sequenceNumber")
                .billingMode("PAY_PER_REQUEST")
                .build());
    }


    private EventBus defineEventBus() {

        return new EventBus(name + "-events");
    }

    private EventBus defineCommandBus() {

        return new EventBus(name + "-commands");
    }

    private Role defineRoleForGatewayToConnectToCommandBus() {

        var assumeGatewayRolePolicyDocument = createAssumeRolePolicyDocument("apigateway.amazonaws.com");

        return new Role("api-gateway-to-" + name + "-command-bus", new RoleArgs.Builder()
                .assumeRolePolicy(assumeGatewayRolePolicyDocument.applyValue(GetPolicyDocumentResult::json))
                .managedPolicyArns("arn:aws:iam::aws:policy/AmazonEventBridgeFullAccess")
                .build());
    }


    private Queue defineQueueForCommandHandler() {

        return new Queue(name + "-command-queue", new QueueArgs.Builder()
                .fifoQueue(true)
                .contentBasedDeduplication(true)
                .visibilityTimeoutSeconds((int) LAMBDA_TIMEOUT.toSeconds()).build());
    }

    private Function defineLambda(Table table, Role lambdaRole) {

        return new Function(name, FunctionArgs
                .builder()
                .runtime("java21")
                .handler(commandHandlerName)
                .role(lambdaRole.arn())
                .code(new FileArchive("./target/lambdas/" + commandHandlerArtifactName))
                .timeout((int) LAMBDA_TIMEOUT.toSeconds())
                .environment(table.name().applyValue(
                        tableName -> FunctionEnvironmentArgs.builder()
                                .variables(Map.of("PERSON_TABLE_NAME", tableName)).build()))
                .build());
    }

    private static Role defineRoleForLambda(Table table) {

        var allowAllOnDynamoForTablePolicyDocument = IamFunctions.getPolicyDocument(GetPolicyDocumentArgs.builder()
                .statements(table.arn().applyValue(tableArn -> Collections.singletonList(GetPolicyDocumentStatementArgs.builder()
                        .effect("Allow")
                        .actions("dynamodb:*")
                        .resources(tableArn)
                        .build())))
                .build());

        var allowAllOnDynamoForTablePolicy = policyForDocument(allowAllOnDynamoForTablePolicyDocument,
                "allow_all_on_dynamodb");

        var assumeLambdaRole = createAssumeRolePolicyDocument("lambda.amazonaws.com");

        return new Role("lambda-role", new RoleArgs.Builder()
                .assumeRolePolicy(assumeLambdaRole.applyValue(GetPolicyDocumentResult::json))
                .managedPolicyArns(allowAllOnDynamoForTablePolicy.arn().applyValue(dynamoArn -> List.of(
                        "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole",
                        dynamoArn)))
                .build());
    }

    private static Output<GetPolicyDocumentResult> createAssumeRolePolicyDocument(String service) {

        return IamFunctions.getPolicyDocument(GetPolicyDocumentArgs.builder()
                .statements(GetPolicyDocumentStatementArgs.builder()
                        .effect("Allow")
                        .actions("sts:AssumeRole")
                        .principals(GetPolicyDocumentStatementPrincipalArgs.builder()
                                .type("Service")
                                .identifiers(service)
                                .build())
                        .build())
                .build());
    }

    private void defineConnectionBetween(EventBus commandBus, Queue commandHandlerQueue) {

        var rule = new EventRule(name, new EventRuleArgs.Builder()
                .eventBusName(commandBus.name())
                .eventPattern("{\"source\": [\"my-event-source\"]}")
                .build());

        new EventTarget(name, new EventTargetArgs.Builder()
                .arn(commandHandlerQueue.arn())
                .sqsTarget(new EventTargetSqsTargetArgs.Builder()
                        .messageGroupId("aggregateId")
                        .build())
                .rule(rule.name())
                .eventBusName(commandBus.name())
                .inputPath("$.detail")
                .build());

        var queuePolicyDocument = Output.all(commandHandlerQueue.arn(), rule.arn()).apply(queueArnAndRuleArn ->

                IamFunctions.getPolicyDocument(GetPolicyDocumentArgs.builder()
                        .statements(GetPolicyDocumentStatementArgs.builder()
                                .effect(Statement.Effect.Allow.name())
                                .principals(GetPolicyDocumentStatementPrincipalArgs.builder()
                                        .type("Service")
                                        .identifiers("events.amazonaws.com")
                                        .build())
                                .actions("sqs:SendMessage")
                                .resources(queueArnAndRuleArn.getFirst())
                                .conditions(GetPolicyDocumentStatementConditionArgs.builder()
                                        .test("ArnEquals")
                                        .variable("aws:SourceArn")
                                        .values(queueArnAndRuleArn.get(1))
                                        .build())
                                .build())
                        .build()));

        new QueuePolicy("allow_command_queue", QueuePolicyArgs.builder()
                .queueUrl(commandHandlerQueue.url())
                .policy(queuePolicyDocument.applyValue(GetPolicyDocumentResult::json))
                .build());
    }

    private void defineConnectionBetween(Queue commandHandlerQueue, Function commandHandler) {

        new Permission(name, new PermissionArgs.Builder()
                .action("lambda:InvokeFunction")
                .principal("sqs.amazonaws.com")
                .function(commandHandler.arn())
                .sourceArn(commandHandlerQueue.arn())
                .build());

        new EventSourceMapping(name, new EventSourceMappingArgs.Builder()
                .eventSourceArn(commandHandlerQueue.arn())
                .functionName(commandHandler.name())
                .build());
    }

    private void definePolicyAndAttachmentToReceiveFromSqs(Queue commandHandlerQueue, Role lambdaRole) {

        var policyDocument = IamFunctions.getPolicyDocument(GetPolicyDocumentArgs.builder()
                .statements(GetPolicyDocumentStatementArgs.builder()
                        .effect("Allow")
                        .actions("sqs:ReceiveMessage",
                                "sqs:DeleteMessage",
                                "sqs:GetQueueAttributes")
                        .resources(commandHandlerQueue.arn().applyValue(List::of))
                        .build())

                .build());

        var allowReceiveFromSqsPolicy = policyForDocument(policyDocument, "receive_from_sqs");

        new RolePolicyAttachment("receive_from_sqs_attachment", new RolePolicyAttachmentArgs.Builder()
                .role(lambdaRole.name())
                .policyArn(allowReceiveFromSqsPolicy.arn())
                .build());
    }

    private static Policy policyForDocument(Output<GetPolicyDocumentResult> policyDocument, String policyName) {

        return new Policy(policyName, new PolicyArgs.Builder()
                .policy(policyDocument.applyValue(GetPolicyDocumentResult::json))
                .build());
    }
}
