package com.appagility.powercircles;

import com.pulumi.aws.apigatewayv2.Api;
import com.pulumi.aws.apigatewayv2.ApiArgs;
import com.pulumi.aws.apigatewayv2.Stage;
import com.pulumi.aws.apigatewayv2.StageArgs;
import com.pulumi.core.Output;
import lombok.Builder;
import lombok.Singular;

import java.util.List;

@Builder
public class EventSourcingApplication {

    private String name;

    @Singular
    private List<Aggregate> aggregates;

    public Output<String> defineInfrastructure() {

        var apiGateway = new Api("PowerCircles", new ApiArgs.Builder().protocolType("HTTP").build());

        var stage = new Stage("stage", new StageArgs.Builder()
                .apiId(apiGateway.id())
                .autoDeploy(true)
                .build()
        );

        aggregates.forEach(a -> a.defineInfrastructure(apiGateway));

        return Output.all(apiGateway.apiEndpoint(), stage.name())
                .applyValue((o) -> o.get(0) + "/" + o.get(1));
    }
}
