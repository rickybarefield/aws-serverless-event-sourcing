package com.appagility.powercircles;

import com.pulumi.Pulumi;
import com.pulumi.core.Output;
import com.pulumi.aws.s3.Bucket;

public class App {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {

            var apiUrl = new Command("person").defineInfrastructure();

            ctx.export("apiUrl", apiUrl);
        });
    }
}
