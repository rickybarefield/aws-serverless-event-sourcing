package com.appagility.powercircles.model;

import com.appagility.powercircles.networking.AwsNetwork;
import com.appagility.powercircles.networking.AwsSubnet;
import com.appagility.powercircles.wrappers.AwsRestApi;
import com.pulumi.aws.apigateway.*;
import com.pulumi.aws.apigateway.inputs.RestApiEndpointConfigurationArgs;
import com.pulumi.core.Output;
import com.pulumi.resources.CustomResourceOptions;
import com.pulumi.resources.Resource;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Singular;

import java.util.ArrayList;
import java.util.List;

@Builder(access = AccessLevel.PUBLIC)
public class EventSourcingApplication {

    private String name;

    @Singular
    private List<Aggregate> aggregates;

    private List<AwsSubnet> dataSubnets;

    private AwsNetwork awsNetwork;

    public Output<String> defineInfrastructure() {

        var restApi = AwsRestApi.builder().name("PowerCircles").build();

        restApi.defineInfrastructure();

        aggregates.forEach(a -> a.defineInfrastructure(restApi, awsNetwork, dataSubnets));

        List<Resource> integrations = new ArrayList<>(restApi.getIntegrations());

        var customOptions = CustomResourceOptions.builder().dependsOn(integrations).build();

        var deployment = new Deployment("deployment", DeploymentArgs.builder()
                .restApi(restApi.getId()).build(),
                customOptions);

        var stage = new Stage("stage", new StageArgs.Builder()
                .restApi(restApi.getId())
                .stageName("dev")
                .deployment(deployment.id())
                .build()
        );

        return stage.invokeUrl();
    }
}
