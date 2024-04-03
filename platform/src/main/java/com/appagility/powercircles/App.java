package com.appagility.powercircles;

import com.pulumi.Pulumi;

public class App {
    public static void main(String[] args) {

        Pulumi.run(ctx -> {

            var application = new EventSourcingApplication.EventSourcingApplicationBuilder()
                    .name("PowerCircles")
                    .aggregate(new Aggregate.AggregateBuilder()
                            .name("person")
                            .command(new Command.CommandBuilder().name("Create").build())
                            .build())
                    .build();

            var apiUrl = application.defineInfrastructure();

            ctx.export("apiUrl", apiUrl);
        });

    }
}
