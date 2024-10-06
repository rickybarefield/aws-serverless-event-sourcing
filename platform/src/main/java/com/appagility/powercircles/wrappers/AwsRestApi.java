package com.appagility.powercircles.wrappers;

import com.amazonaws.HttpMethod;
import com.appagility.powercircles.common.MayBecome;
import com.pulumi.aws.apigateway.*;
import com.pulumi.aws.apigateway.inputs.RestApiEndpointConfigurationArgs;
import com.pulumi.aws.lambda.Function;
import com.pulumi.aws.lambda.Permission;
import com.pulumi.aws.lambda.PermissionArgs;
import com.pulumi.core.Output;
import com.pulumi.resources.CustomResourceOptions;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AwsRestApi {

    private final String name;

    private MayBecome<RestApi> restApi = MayBecome.empty("restApi");

    @Getter
    private List<Integration> integrations = new ArrayList<>();

    @Builder
    public AwsRestApi(String name) {

        this.name = name;
    }

    public Output<String> getId() {

        return restApi.get().id();
    }

    public void defineInfrastructure() {

        restApi.set(new RestApi("PowerCircles", RestApiArgs.builder()
                .endpointConfiguration(RestApiEndpointConfigurationArgs.builder()
                        .types("REGIONAL")
                        .build())
                .build()));
    }

    public void defineRouteToFunction(String path, HttpMethod httpMethod, Function handler) {

        String nameContext = name + "-" + path;

        var resource = new Resource(nameContext, ResourceArgs.builder()
                .restApi(restApi.get().id())
                .parentId(restApi.get().rootResourceId())
                .pathPart(path)
                .build());

        var method = new Method(nameContext, MethodArgs.builder()
                .resourceId(resource.id())
                .restApi(restApi.get().id())
                .httpMethod(httpMethod.name())
                .authorization("NONE")
                .build());

        var integration = new Integration(nameContext, IntegrationArgs.builder()
                .type("AWS")
                .restApi(restApi.get().id())
                .resourceId(resource.id())
                .httpMethod(method.httpMethod())
                .integrationHttpMethod("POST")
                .uri(handler.invokeArn())
                .build());

        integrations.add(integration);

        new MethodResponse(nameContext, MethodResponseArgs.builder()
                .restApi(restApi.get().id())
                .resourceId(resource.id())
                .httpMethod(method.httpMethod())
                .statusCode("200")
                .build());

        new IntegrationResponse(nameContext, IntegrationResponseArgs.builder()
                .restApi(restApi.get().id())
                .resourceId(resource.id())
                .statusCode("200")
                .httpMethod(method.httpMethod())
                .build(), CustomResourceOptions.builder().dependsOn(integration).build());

        allowToInvokeFunction(nameContext, handler);
    }

    private void allowToInvokeFunction(String nameContext, Function function) {

        new Permission(nameContext + "_invoke_lambda", PermissionArgs.builder()
                .action("lambda:InvokeFunction")
                .function(function.name())
                .principal("apigateway.amazonaws.com")
                .sourceArn(restApi.get().executionArn().applyValue(arn -> arn + "/*/*"))
                .build());
    }
}
