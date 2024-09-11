package com.appagility.powercircles;

import com.appagility.aws.lambda.SqlExecutor;
import com.appagility.powercircles.networking.AwsNetwork;
import com.appagility.powercircles.networking.AwsSubnet;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.pulumi.asset.FileArchive;
import com.pulumi.aws.ec2.SecurityGroup;
import com.pulumi.aws.ec2.SecurityGroupArgs;
import com.pulumi.aws.iam.Role;
import com.pulumi.aws.iam.RoleArgs;
import com.pulumi.aws.iam.outputs.GetPolicyDocumentResult;
import com.pulumi.aws.lambda.Function;
import com.pulumi.aws.lambda.FunctionArgs;
import com.pulumi.aws.lambda.Invocation;
import com.pulumi.aws.lambda.InvocationArgs;
import com.pulumi.aws.lambda.enums.Runtime;
import com.pulumi.aws.lambda.inputs.FunctionEnvironmentArgs;
import com.pulumi.aws.lambda.inputs.FunctionVpcConfigArgs;
import com.pulumi.aws.rds.Proxy;
import com.pulumi.aws.rds.ProxyArgs;
import com.pulumi.aws.rds.inputs.ProxyAuthArgs;
import com.pulumi.core.Output;
import lombok.Builder;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.appagility.powercircles.connectionfactories.RdsPostgresSecretAuthConnectionFactory.SECRET_NAME_ENV_VARIABLE;

@Builder
public class DatabaseSchemaInitializer {

    private static final String SQL_EXECUTOR_ARTIFACT_NAME = "aws-lambda-sql-executor.jar";
    private static final Duration EXECUTOR_TIMEOUT = Duration.ofMinutes(2);
    private static final String RESOURCE_NAME = "schema-initializer";

    private final AwsNetwork awsNetwork;
    private final List<AwsSubnet> dataSubnets;
    private final AwsRdsInstance instance;

    private Function sqlExecutor;

    public void defineInfrastructure() {

//        defineRdsProxy();
        Role role = defineRoleForLambda();
        defineLambdaForExecutingSql(role);
    }

    //TODO Not convinced we need a proxy for this use, we will for the actual projection lambda
    private void defineRdsProxy() {

        new Proxy(instance + "-proxy-sql-executor", ProxyArgs.builder()
                .engineFamily("postgres")
                .auths(List.of(ProxyAuthArgs.builder().build()))
                .build());
    }

    private void defineLambdaForExecutingSql(Role lambdaRole) {

        var subnetIds = Output.all(dataSubnets.stream().map(AwsSubnet::getId).toList());

        var vpcId = dataSubnets.get(0).getVpcId();

        var lambdaSecurityGroup = new SecurityGroup(instance.getName() + "-schema-initializer", SecurityGroupArgs.builder()
                .vpcId(vpcId)
                .build());

        awsNetwork.allowAccessToSecretsManager(RESOURCE_NAME, lambdaSecurityGroup);

        instance.allowAccessFrom(RESOURCE_NAME, lambdaSecurityGroup);

        var securityGroupIds = lambdaSecurityGroup.id().applyValue(Collections::singletonList);

        sqlExecutor = new Function(instance.getName() + "-" + RESOURCE_NAME, FunctionArgs
                .builder()
                .runtime(Runtime.Java21)
                .handler(SqlExecutor.class.getName())
                .role(lambdaRole.arn())
                .code(new FileArchive("./target/lambdas/" + SQL_EXECUTOR_ARTIFACT_NAME))
                .timeout((int) EXECUTOR_TIMEOUT.toSeconds())
                .environment(instance.getConnectionDetails().applyValue(
                        connectionDetails -> FunctionEnvironmentArgs.builder()
                                .variables(Map.of("DB_URL", connectionDetails.url(),
                                        "DB_USERNAME", instance.getUsername(),
                                        "DB_PORT", connectionDetails.port(),
                                                SECRET_NAME_ENV_VARIABLE, connectionDetails.secretName()))
                                .build()))
                .vpcConfig(FunctionVpcConfigArgs.builder()
                        .subnetIds(subnetIds)
                        .securityGroupIds(securityGroupIds)
                        .build())
                .build());
    }

    private Role defineRoleForLambda() {

        var allowConnectToDatabasePolicy = instance.createPolicyToGetRootUserSecret(RESOURCE_NAME);

        var assumeLambdaRole = IamPolicyFunctions.createAssumeRolePolicyDocument("lambda.amazonaws.com");

        return new Role("initializer-lambda-role-" + instance.getName(), new RoleArgs.Builder()
                .assumeRolePolicy(assumeLambdaRole.applyValue(GetPolicyDocumentResult::json))
                .managedPolicyArns(allowConnectToDatabasePolicy.arn().applyValue(rdsPolicyArn -> List.of(
                        "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole",
                        rdsPolicyArn)))
                .build());
    }

    public void execute(String logicalName, InputStream resourceContainingSql) {

        try {

            var sqlString = IOUtils.toString(resourceContainingSql);


            var gson = new Gson();

            var input = new JsonObject();
            input.add("sql", new JsonPrimitive(sqlString));

            new Invocation(instance.getName() + "-" + logicalName, InvocationArgs.builder()
                    .functionName(sqlExecutor.name())
                    .input(gson.toJson(input))
                    .build()
            );

        } catch (IOException e) {

            throw new RuntimeException(e);
        }

    }
}
