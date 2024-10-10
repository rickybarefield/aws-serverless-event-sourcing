package com.appagility.powercircles.model;

import com.appagility.powercircles.common.NamingContext;
import com.appagility.powercircles.networking.AwsNetwork;
import com.appagility.powercircles.networking.AwsSubnet;
import com.appagility.powercircles.wrappers.AwsRestApi;
import com.pulumi.aws.apigateway.Deployment;
import com.pulumi.aws.apigateway.DeploymentArgs;
import com.pulumi.aws.apigateway.Stage;
import com.pulumi.aws.apigateway.StageArgs;
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

    @Singular
    private List<Aggregate> aggregates;

    private List<AwsSubnet> dataSubnets;

    private AwsNetwork awsNetwork;

    private NamingContext namingContext;

    public Output<String> defineInfrastructure() {

        var restApi = new AwsRestApi();

        restApi.defineInfrastructure(namingContext);

        aggregates.forEach(a -> a.defineInfrastructure(namingContext, restApi, awsNetwork, dataSubnets));

        List<Resource> integrations = new ArrayList<>(restApi.getIntegrations());

        var customOptions = CustomResourceOptions.builder().dependsOn(integrations).build();

        var deployment = new Deployment(namingContext.getName(), DeploymentArgs.builder()
                .restApi(restApi.getId()).build(),
                customOptions);

        var stage = new Stage(namingContext.getName(), new StageArgs.Builder()
                .restApi(restApi.getId())
                .stageName("dev")
                .deployment(deployment.id())
                .build()
        );

        return stage.invokeUrl();
    }
}
