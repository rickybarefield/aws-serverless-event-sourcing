package com.appagility.powercircles;

import com.appagility.powercircles.commandhandlers.infrastructure.aws.PersonHandler;
import com.appagility.powercircles.common.ContextNamingStrategy;
import com.appagility.powercircles.model.*;
import com.appagility.powercircles.networking.AwsNetwork;
import com.appagility.powercircles.networking.NetworkingInputs;
import com.appagility.powercircles.summaryprojection.infrastructure.aws.LambdaEventHandler;
import com.appagility.powercircles.summaryprojection.infrastructure.aws.LambdaQueryHandler;
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

            var application = EventSourcingApplication.builder()
                    .name("PowerCircles")
                    .awsNetwork(network)
                    .dataSubnets(dataSubnets)
                    .aggregate(Aggregate.builder()
                            .name("person")
                            .commandHandler(PersonHandler.class)
                            .commandHandlerArtifactName("power-circles-command-handlers.jar")
                            .command(Command.builder().name("person").build())
                            .projection(Projection.builder()
                                    .name("Summary")
                                    .projectionHandlerArtifactName("power-circles-summary-projections.jar")
                                    .projectionHandler(LambdaEventHandler.class)
                                    //FIXME This should work without qualified path as using classloader of projectionHandler
                                    .schemaResourcePath("com/appagility/powercircles/summaryprojection/infrastructure/aws/schema.sql")
                                    .schemaName("summary")
                                    .queryHandler(LambdaQueryHandler.class)
                                    .query(Query.builder().name("person-summary").build())
                                    .build())
                            .build())
                    .build();

            var apiUrl = application.defineInfrastructure();

            ctx.export("apiUrl", apiUrl);

        });

    }
}
