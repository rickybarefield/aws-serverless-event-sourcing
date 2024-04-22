package com.appagility.powercircles;

import com.pulumi.aws.apigateway.*;
import com.pulumi.aws.cloudwatch.EventBus;
import com.pulumi.aws.iam.Role;
import com.pulumi.aws.lambda.Function;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

public class Command {

    private String name;

    @Getter
    private Integration integration;

    @Getter
    private Method method;

    @Builder
    public Command(String name) {

        this.name = name;
    }

    public void defineRouteAndConnectToCommandBus(RestApi restApi, Function commandHandler) {


        var resource = new Resource(name, ResourceArgs.builder()
                .restApi(restApi.id())
                .parentId(restApi.rootResourceId())
                .pathPart(name)
                .build());

        method = new Method(name + "-POST", MethodArgs.builder()
                .resourceId(resource.id())
                .restApi(restApi.id())
                .httpMethod("POST")
                .authorization("NONE")
                .build());

        integration = new Integration(name, IntegrationArgs.builder()
                .type("AWS")
                .restApi(restApi.id())
                .resourceId(resource.id())
                .httpMethod("POST")
                .integrationHttpMethod("POST")
                .uri(commandHandler.invokeArn())
                .build());



//        var integration = new Integration(name, IntegrationArgs.builder()
//                .apiId(apiGateway.id())
//                .integrationType("AWS_PROXY")
//                .integrationSubtype("EventBridge-PutEvents")
//                .requestParameters(commandBus.name().applyValue(n ->  Map.of(
//                        "EventBusName", n,
//                        "Detail", "$request.body",
//                        "DetailType", "my-detail-type",
//                        "Source", "my-event-source"
//                )))
//                .credentialsArn(roleForGatewayToConnectToBus.arn())
//                .build());
//
//        new Route(name, RouteArgs.builder()
//                .apiId(apiGateway.id())
//                .routeKey("POST /" + name)
//                .target(integration.id().applyValue(t -> "integrations/" + t))
//                .build());

    }
}
