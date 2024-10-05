package com.appagility.powercircles.model;

import com.appagility.powercircles.wrappers.AwsRestApi;
import com.pulumi.aws.lambda.Function;
import lombok.Builder;

@Builder
public class Query {

    private final String name;

    public void defineRouteAndConnectToQueryHandler(AwsRestApi restApi, Function queryHandlerLambda) {

        restApi.defineRouteToFunction(name, queryHandlerLambda);
    }

    //TODO In future these should take a body and use OpenAPI specs
}
