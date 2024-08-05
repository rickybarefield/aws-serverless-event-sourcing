package com.appagility.powercircles;

import com.appagility.powercircles.commandhandlers.infrastructure.aws.PersonHandler;
import com.pulumi.Pulumi;

public class App {
    public static void main(String[] args) {

        Pulumi.run(ctx -> {

            var application = new EventSourcingApplication.EventSourcingApplicationBuilder()
                    .name("PowerCircles")
                    .aggregate(Aggregate.builder()
                            .name("person")
                            .commandHandlerName(PersonHandler.class.getName())
                            .commandHandlerArtifactName("power-circles-command-handlers.jar")
                            .command(Command.builder().name("Create").build())
                            .projection(Projection.builder().name("Summary").schema("").build())
                            .build())
                    .build();

            var apiUrl = application.defineInfrastructure();

            ctx.export("apiUrl", apiUrl);

        });

    }
}
