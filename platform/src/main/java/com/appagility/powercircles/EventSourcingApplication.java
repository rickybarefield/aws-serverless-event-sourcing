package com.appagility.powercircles;

import com.pulumi.aws.apigateway.*;
import com.pulumi.aws.apigateway.inputs.RestApiEndpointConfigurationArgs;
import com.pulumi.core.Output;
import com.pulumi.resources.CustomResourceOptions;
import com.pulumi.resources.Resource;
import lombok.Builder;
import lombok.Singular;

import java.util.List;

@Builder
public class EventSourcingApplication {

    private String name;

    @Singular
    private List<Aggregate> aggregates;

    private List<AwsSubnet> dataSubnets;

    public Output<String> defineInfrastructure() {

        var restApi = new RestApi("PowerCircles", RestApiArgs.builder()
                .endpointConfiguration(RestApiEndpointConfigurationArgs.builder()
                        .types("REGIONAL")
                        .build())
                .build());

        aggregates.forEach(a -> a.defineInfrastructure(restApi, dataSubnets));

        var integrations = aggregates.stream()
                .flatMap(a -> a.getCommands().stream().map( c -> (Resource) c.getIntegration()))
                .toList();

        var customOptions = CustomResourceOptions.builder().dependsOn(integrations).build();


        var deployment = new Deployment("deployment", DeploymentArgs.builder()
                .restApi(restApi.id()).build(),
                customOptions);

        var stage = new Stage("stage", new StageArgs.Builder()
                .restApi(restApi.id())
                .stageName("dev")
                .deployment(deployment.id())
                .build()
        );

        return stage.invokeUrl();
    }
}
