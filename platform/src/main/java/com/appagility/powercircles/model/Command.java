package com.appagility.powercircles.model;

import com.amazonaws.HttpMethod;
import com.appagility.powercircles.common.NamingContext;
import com.appagility.powercircles.wrappers.AwsRestApi;
import com.pulumi.aws.lambda.Function;
import lombok.Builder;

public class Command {

    private final String name;

    @Builder
    public Command(String name) {

        this.name = name;
    }

    public void defineRouteAndConnectToCommandHandler(NamingContext parentNamingContext, AwsRestApi restApi, Function commandHandler) {

        restApi.defineRouteToFunction(parentNamingContext, name, HttpMethod.POST, commandHandler);
    }
}
