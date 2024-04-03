package com.appagility.powercircles;

import com.pulumi.asset.FileArchive;
import com.pulumi.aws.apigatewayv2.*;
import com.pulumi.aws.cloudwatch.*;
import com.pulumi.aws.iam.Role;
import com.pulumi.aws.iam.RoleArgs;
import com.pulumi.aws.lambda.Function;
import com.pulumi.aws.lambda.FunctionArgs;
import com.pulumi.aws.lambda.Permission;
import com.pulumi.aws.lambda.PermissionArgs;
import lombok.Builder;

import java.util.Map;

import static com.pulumi.codegen.internal.Serialization.*;

@Builder
public class Command {

    private String name;

    public Command(String name) {

        this.name = name;
    }

    public void defineInfrastructure(Api apiGateway, Role roleForGatewayToConnectToBus, EventBus commandBus) {

        var rule = new EventRule(name, new EventRuleArgs.Builder()
                .eventBusName(commandBus.name())
                .eventPattern("{\"source\": [\"my-event-source\"]}")
                .build());

        var integration = new Integration(name, IntegrationArgs.builder()
                .apiId(apiGateway.id())
                .integrationType("AWS_PROXY")
                .integrationSubtype("EventBridge-PutEvents")
                .requestParameters(commandBus.name().applyValue(n ->  Map.of(
                        "EventBusName", n,
                        "Detail", "$request.body",
                        "DetailType", "my-detail-type",
                        "Source", "my-event-source"
                )))
                .credentialsArn(roleForGatewayToConnectToBus.arn())
                .build());

        new Route(name, RouteArgs.builder()
                .apiId(apiGateway.id())
                .routeKey("POST /" + name)
                .target(integration.id().applyValue(t -> "integrations/" + t))
                .build());

        var lambdaRole = new Role("lambda-role", new RoleArgs.Builder()
                .assumeRolePolicy(serializeJson(
                        jsonObject(
                                jsonProperty("Version", "2012-10-17"),
                                jsonProperty("Statement", jsonArray(jsonObject(
                                        jsonProperty("Action", "sts:AssumeRole"),
                                        jsonProperty("Effect", "Allow"),
                                        jsonProperty("Principal", jsonObject(
                                                jsonProperty("Service", "lambda.amazonaws.com")
                                        ))))))))
                .managedPolicyArns("arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole")
                .build());

        var executionLambda = new Function(name, FunctionArgs
                .builder()
                .runtime("java21")
                .handler("com.appagility.powercircles.commandhandlers.PersonHandler::handleRequest")
                .role(lambdaRole.arn())
                .code(new FileArchive("/home/ricky/projects/crm/platform/target/lambdas/power-circles-command-handlers.jar"))
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
