package com.appagility.powercircles;

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
import com.pulumi.aws.iam.inputs.RoleInlinePolicyArgs;
import com.pulumi.aws.iam.outputs.GetPolicyDocumentResult;
import com.pulumi.aws.lambda.*;
import com.pulumi.aws.lambda.inputs.FunctionEnvironmentArgs;
import com.pulumi.aws.sqs.Queue;
import com.pulumi.aws.sqs.QueueArgs;
import lombok.Builder;
import lombok.Singular;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.pulumi.codegen.internal.Serialization.*;

@Builder
public class Aggregate {

    public static final Duration LAMBDA_TIMEOUT = Duration.ofMinutes(1);
    private String name;

    @Singular
    private List<Command> commands;

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

        //TODO Should probably just have content in JSON
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

        return new Role("api-gateway-to-" + name + "-command-bus", new RoleArgs.Builder()
                .assumeRolePolicy(serializeJson(
                        jsonObject(
                                jsonProperty("Version", "2012-10-17"),
                                jsonProperty("Statement", jsonArray(jsonObject(
                                        jsonProperty("Action", "sts:AssumeRole"),
                                        jsonProperty("Effect", "Allow"),
                                        jsonProperty("Principal", jsonObject(
                                                jsonProperty("Service", "apigateway.amazonaws.com")
                                        ))))))))
                .managedPolicyArns("arn:aws:iam::aws:policy/AmazonEventBridgeFullAccess")
                .build());
    }


    private Queue defineQueueForCommandHandler() {

        return new Queue(name + "-command-queue", new QueueArgs.Builder()
                .fifoQueue(true)
                .visibilityTimeoutSeconds((int) LAMBDA_TIMEOUT.toSeconds()).build());
    }

    private Function defineLambda(Table table, Role lambdaRole) {

        var executionLambda = new Function(name, FunctionArgs
                .builder()
                .runtime("java21")
                .handler("com.appagility.powercircles.commandhandlers.infrastructure.aws.PersonHandler::handleRequest")
                .role(lambdaRole.arn())
                .code(new FileArchive("./target/lambdas/power-circles-command-handlers.jar"))
                .timeout((int) LAMBDA_TIMEOUT.toSeconds())
                .environment(table.name().applyValue(
                        tableName -> FunctionEnvironmentArgs.builder()
                                .variables(Map.of("PERSON_TABLE_NAME", tableName)).build()))
                .build());

        return executionLambda;
    }

    private static Role defineRoleForLambda(Table table) {

        return new Role("lambda-role", new RoleArgs.Builder()
                .inlinePolicies(RoleInlinePolicyArgs.builder()
                        .name("access_table")
                        .policy(table.arn().applyValue(tableArn ->
                                serializeJson(
                                        jsonObject(
                                                jsonProperty("Version", "2012-10-17"),
                                                jsonProperty("Statement", jsonArray(jsonObject(
                                                        jsonProperty("Action", jsonArray("dynamodb:*")),
                                                        jsonProperty("Effect", "Allow"),
                                                        jsonProperty("Resource", tableArn)
                                                )))))
                        ))
                        .build())
                .assumeRolePolicy(serializeJson(
                        jsonObject(
                                jsonProperty("Version", "2012-10-17"),
                                jsonProperty("Statement", jsonArray(
                                        jsonObject(
                                                jsonProperty("Action", "sts:AssumeRole"),
                                                jsonProperty("Effect", "Allow"),
                                                jsonProperty("Principal", jsonObject(
                                                        jsonProperty("Service", "lambda.amazonaws.com")
                                                )))
                                )))))
                .managedPolicyArns("arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole")
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

        var allowReceiveFromSqsPolicy = new Policy("receive_from_sqs", new PolicyArgs.Builder()
                .policy(policyDocument.applyValue(GetPolicyDocumentResult::json))
                .build());

        new RolePolicyAttachment("receive_from_sqs_attachment", new RolePolicyAttachmentArgs.Builder()
                .role(lambdaRole.name())
                .policyArn(allowReceiveFromSqsPolicy.arn())
                .build());
    }

}
