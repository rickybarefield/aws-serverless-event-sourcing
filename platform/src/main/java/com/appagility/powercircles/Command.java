package com.appagility.powercircles;

import com.pulumi.aws.apigatewayv2.*;
import com.pulumi.aws.cloudwatch.EventBus;
import com.pulumi.aws.iam.Role;
import lombok.Builder;

import java.util.Map;

@Builder
public class Command {

    private String name;

    public Command(String name) {

        this.name = name;
    }

    public void defineInfrastructure(Api apiGateway, Role roleForGatewayToConnectToBus, EventBus commandBus) {


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

    }
}
