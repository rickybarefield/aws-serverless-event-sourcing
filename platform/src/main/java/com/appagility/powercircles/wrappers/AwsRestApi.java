package com.appagility.powercircles.wrappers;

import com.amazonaws.HttpMethod;
import com.appagility.powercircles.common.MayBecome;
import com.appagility.powercircles.common.NamingContext;
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


public class AwsRestApi {

    private MayBecome<RestApi> restApi = MayBecome.empty("restApi");

    @Getter
    private List<Integration> integrations = new ArrayList<>();

    public Output<String> getId() {

        return restApi.get().id();
    }

    public void defineInfrastructure(NamingContext namingContext) {

        restApi.set(new RestApi(namingContext.getName(), RestApiArgs.builder()
                .endpointConfiguration(RestApiEndpointConfigurationArgs.builder()
                        .types("REGIONAL")
                        .build())
                .build()));
    }

    public void defineRouteToFunction(NamingContext parentNamingContext, String path, HttpMethod httpMethod, Function handler) {

        var routeNamingContext = parentNamingContext.with(httpMethod.name()).with(path);

        var resource = new Resource(routeNamingContext.getName(), ResourceArgs.builder()
                .restApi(restApi.get().id())
                .parentId(restApi.get().rootResourceId())
                .pathPart(path)
                .build());

        var method = new Method(routeNamingContext.getName(), MethodArgs.builder()
                .resourceId(resource.id())
                .restApi(restApi.get().id())
                .httpMethod(httpMethod.name())
                .authorization("NONE")
                .build());

        var integration = new Integration(routeNamingContext.getName(), IntegrationArgs.builder()
                .type("AWS")
                .restApi(restApi.get().id())
                .resourceId(resource.id())
                .httpMethod(method.httpMethod())
                .integrationHttpMethod("POST")
                .uri(handler.invokeArn())
                .build());

        integrations.add(integration);

        new MethodResponse(routeNamingContext.getName(), MethodResponseArgs.builder()
                .restApi(restApi.get().id())
                .resourceId(resource.id())
                .httpMethod(method.httpMethod())
                .statusCode("200")
                .build());

        new IntegrationResponse(routeNamingContext.getName(), IntegrationResponseArgs.builder()
                .restApi(restApi.get().id())
                .resourceId(resource.id())
                .statusCode("200")
                .httpMethod(method.httpMethod())
                .build(), CustomResourceOptions.builder().dependsOn(integration).build());

        allowToInvokeFunction(routeNamingContext, handler);
    }

    private void allowToInvokeFunction(NamingContext namingContext, Function function) {

        new Permission(namingContext.getName(), PermissionArgs.builder()
                .action("lambda:InvokeFunction")
                .function(function.name())
                .principal("apigateway.amazonaws.com")
                .sourceArn(restApi.get().executionArn().applyValue(arn -> arn + "/*/*"))
                .build());
    }
}
