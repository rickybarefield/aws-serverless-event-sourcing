package com.appagility.powercircles.model;

import com.appagility.powercircles.wrappers.AwsRestApi;
import com.pulumi.aws.apigateway.*;
import com.pulumi.aws.lambda.Function;
import com.pulumi.resources.CustomResourceOptions;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

public class Command {

    private final String name;


    @Builder
    public Command(String name) {

        this.name = name;
    }

    public void defineRouteAndConnectToCommandHandler(AwsRestApi restApi, Function commandHandler) {

        restApi.defineRouteToFunction(name, commandHandler);
    }
}
