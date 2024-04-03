package com.appagility.powercircles;

import com.pulumi.aws.apigatewayv2.Api;
import com.pulumi.aws.cloudwatch.EventBus;
import com.pulumi.aws.iam.Role;
import com.pulumi.aws.iam.RoleArgs;
import lombok.Builder;
import lombok.Singular;

import java.util.List;

import static com.pulumi.codegen.internal.Serialization.*;

@Builder
public class Aggregate {

    private String name;

    @Singular
    private List<Command> commands;

    public void defineInfrastructure(Api apiGateway) {

        String eventBusName = name + "-events";
        var eventBus = new EventBus(eventBusName);

        String commandBusName = name + "-commands";
        var commandBus = new EventBus(commandBusName);

        var roleForGatewayToConnectToBus = new Role("api-gateway-to-" + name + "-command-bus", new RoleArgs.Builder()
                .assumeRolePolicy(serializeJson(
                        jsonObject(
                                jsonProperty("Version", "2012-10-17"),
                                jsonProperty("Statement", jsonArray(jsonObject(
                                        jsonProperty("Action", "sts:AssumeRole"),
                                        jsonProperty("Effect", "Allow"),
                                        jsonProperty("Principal", jsonObject(
                                                jsonProperty("Service", "apigateway.amazonaws.com")
                                        ))))))))
                .managedPolicyArns("arn:aws:iam::aws:policy/AmazonEventBridgeFullAccess")
                .build());

        commands.forEach(c -> c.defineInfrastructure(apiGateway, roleForGatewayToConnectToBus, commandBus));
    }
}
