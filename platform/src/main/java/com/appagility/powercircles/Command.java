package com.appagility.powercircles;

import com.pulumi.aws.apigateway.*;
import com.pulumi.aws.lambda.Function;
import com.pulumi.resources.CustomResourceOptions;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

public class Command {

    private final String name;

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
                .httpMethod(method.httpMethod())
                .integrationHttpMethod("POST")
                .uri(commandHandler.invokeArn())
                .build());

        new MethodResponse(name, MethodResponseArgs.builder()
                .restApi(restApi.id())
                .resourceId(resource.id())
                .httpMethod(method.httpMethod())
                .statusCode("200")
                .build());

        new IntegrationResponse(name, IntegrationResponseArgs.builder()
                .restApi(restApi.id())
                .resourceId(resource.id())
                .statusCode("200")
                .httpMethod(method.httpMethod())
                .responseTemplates(Map.of("application/json", "{ \"dummy\": \"value\" }"))
                .build(), CustomResourceOptions.builder().dependsOn(integration).build());
    }
}
