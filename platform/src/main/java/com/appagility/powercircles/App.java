package com.appagility.powercircles;

import com.appagility.powercircles.commandhandlers.infrastructure.aws.PersonHandler;
import com.appagility.powercircles.summaryprojection.infrastructure.aws.LambdaEventHandler;
import com.pulumi.Pulumi;

public class App {
    public static void main(String[] args) {

        Pulumi.run(ctx -> {

            var contextNamingStrategy = new ContextNamingStrategy(ctx);

            var inputs = NetworkingInputs.deserialize(ctx);
            var network =
                    AwsNetwork.builder()
                            .namingStrategy(contextNamingStrategy)
                            .name("main")
                            .tierNames("data")
                            .networkRange(inputs.getNetworkCidrRange())
                            .buildWithEvenlySplitSubnetsAcrossAllAvailabilityZones();

            network.defineInfrastructure();

            var dataSubnets = network.getSubnets("data");

            var application = new EventSourcingApplication.EventSourcingApplicationBuilder()
                    .name("PowerCircles")
                    .awsNetwork(network)
                    .dataSubnets(dataSubnets)
                    .aggregate(Aggregate.builder()
                            .name("person")
                            .commandHandlerName(PersonHandler.class.getName())
                            .commandHandlerArtifactName("power-circles-command-handlers.jar")
                            .command(Command.builder().name("Create").build())
                            .projection(Projection.builder()
                                    .name("Summary")
                                    .projectionHandlerArtifactName("power-circles-summary-projection.jar")
                                    .projectionHandler(LambdaEventHandler.class)
                                    .schemaResourcePath("/schema.sql")
                                    .build())
                            .build())
                    .build();

            var apiUrl = application.defineInfrastructure();

            ctx.export("apiUrl", apiUrl);

        });

    }
}
