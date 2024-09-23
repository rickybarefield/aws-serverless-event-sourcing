package com.appagility.powercircles.wrappers;

import com.pulumi.asset.FileArchive;
import com.pulumi.aws.iam.Role;
import com.pulumi.aws.lambda.Function;
import com.pulumi.aws.lambda.FunctionArgs;
import com.pulumi.aws.lambda.enums.Runtime;
import com.pulumi.aws.lambda.inputs.FunctionEnvironmentArgs;
import com.pulumi.aws.lambda.inputs.FunctionVpcConfigArgs;
import com.pulumi.core.Output;
import lombok.Builder;

import java.time.Duration;

@Builder
public class JavaLambda {

    public static final Duration LAMBDA_TIMEOUT = Duration.ofMinutes(1);

    private final String name;
    private final Class<?> handler;
    private final String artifactName;
    private final Role role;
    private Output<FunctionEnvironmentArgs> environment;
    private FunctionVpcConfigArgs vpcConfig;

    public Function define() {

        var functionArgsBuilder = FunctionArgs
                .builder()
                .runtime(Runtime.Java21)
                .handler(handler.getName())
                .role(role.arn())
                .code(new FileArchive("./target/lambdas/" + artifactName))
                .timeout((int) LAMBDA_TIMEOUT.toSeconds())
                .environment(environment);

        if(vpcConfig != null) {

            functionArgsBuilder.vpcConfig(vpcConfig);
        }

        return new Function(name, functionArgsBuilder.build());
    }

}
