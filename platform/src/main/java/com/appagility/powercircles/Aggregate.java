package com.appagility.powercircles;

import com.pulumi.asset.FileArchive;
import com.pulumi.aws.apigatewayv2.Api;
import com.pulumi.aws.cloudwatch.*;
import com.pulumi.aws.dynamodb.Table;
import com.pulumi.aws.dynamodb.TableArgs;
import com.pulumi.aws.dynamodb.inputs.TableAttributeArgs;
import com.pulumi.aws.iam.Role;
import com.pulumi.aws.iam.RoleArgs;
import com.pulumi.aws.iam.inputs.RoleInlinePolicyArgs;
import com.pulumi.aws.lambda.Function;
import com.pulumi.aws.lambda.FunctionArgs;
import com.pulumi.aws.lambda.Permission;
import com.pulumi.aws.lambda.PermissionArgs;
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

    private String name;

    @Singular
    private List<Command> commands;

    public void defineInfrastructure(Api apiGateway) {

        var table = defineDynamoTable();

        defineEventBus();

        var commandBus = defineCommandBus();

        var roleForGatewayToConnectToBus = defineRoleForGatewayToConnectToCommandBus();

        defineQueueForCommandHandler();

        defineLamdbaAndConnectToLambda(table, commandBus);

        commands.forEach(c -> c.defineInfrastructure(
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


    private void defineQueueForCommandHandler() {

        new Queue(name + "-command-queue", new QueueArgs.Builder().fifoQueue(true).build());
    }

    private void defineLamdbaAndConnectToLambda(Table table, EventBus commandBus) {

        var lambdaRole = new Role("lambda-role", new RoleArgs.Builder()
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

        var executionLambda = new Function(name, FunctionArgs
                .builder()
                .runtime("java21")
                .handler("com.appagility.powercircles.commandhandlers.infrastructure.aws.PersonHandler::handleRequest")
                .role(lambdaRole.arn())
                .code(new FileArchive("./target/lambdas/power-circles-command-handlers.jar"))
                .timeout((int) Duration.ofMinutes(1).toSeconds())
                .environment(table.name().applyValue(
                        tableName -> FunctionEnvironmentArgs.builder()
                                .variables(Map.of("PERSON_TABLE_NAME", tableName)).build()))
                .build());

        var rule = new EventRule(name, new EventRuleArgs.Builder()
                .eventBusName(commandBus.name())
                .eventPattern("{\"source\": [\"my-event-source\"]}")
                .build());

        new Permission(name, new PermissionArgs.Builder()
                .action("lambda:InvokeFunction")
                .principal("events.amazonaws.com")
                .function(executionLambda.arn())
                .sourceArn(rule.arn())
                .build());

        new EventTarget(name, new EventTargetArgs.Builder()
                .arn(executionLambda.arn())
                .rule(rule.name())
                .eventBusName(commandBus.name())
                .inputPath("$.detail")
                .build());
    }

}
