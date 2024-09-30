package com.appagility.powercircles.wrappers;

import com.appagility.aws.lambda.SqlExecutor;
import com.appagility.powercircles.common.IamPolicyFunctions;
import com.appagility.powercircles.common.ManagedPolicies;
import com.appagility.powercircles.networking.AwsNetwork;
import com.appagility.powercircles.networking.AwsSubnet;
import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.pulumi.aws.ec2.SecurityGroup;
import com.pulumi.aws.ec2.SecurityGroupArgs;
import com.pulumi.aws.iam.Role;
import com.pulumi.aws.iam.RoleArgs;
import com.pulumi.aws.iam.outputs.GetPolicyDocumentResult;
import com.pulumi.aws.lambda.Function;
import com.pulumi.aws.lambda.Invocation;
import com.pulumi.aws.lambda.InvocationArgs;
import com.pulumi.aws.lambda.inputs.FunctionEnvironmentArgs;
import com.pulumi.aws.lambda.inputs.FunctionVpcConfigArgs;
import com.pulumi.core.Output;
import com.pulumi.resources.CustomResourceOptions;
import com.pulumi.resources.Resource;
import lombok.Builder;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.appagility.powercircles.connectionfactories.RdsPostgresSecretAuthConnectionFactory.SECRET_NAME_ENV_VARIABLE;

public class DatabaseSqlExecutor {

    private static final String SQL_EXECUTOR_ARTIFACT_NAME = "aws-lambda-sql-executor.jar";
    private static final Duration EXECUTOR_TIMEOUT = Duration.ofMinutes(2);
    private static final String RESOURCE_NAME = "schema-initializer";

    private final AwsNetwork awsNetwork;
    private final List<AwsSubnet> dataSubnets;
    private final AwsRdsInstance instance;

    private final List<Resource> previousExecutions = new ArrayList<>();

    @Builder
    public DatabaseSqlExecutor(AwsNetwork awsNetwork, List<AwsSubnet> dataSubnets, AwsRdsInstance instance, Function sqlExecutor) {

        this.awsNetwork = awsNetwork;
        this.dataSubnets = dataSubnets;
        this.instance = instance;
        this.sqlExecutor = sqlExecutor;
    }

    private Function sqlExecutor;



    public void defineInfrastructure() {

        Role role = defineRoleForLambda();
        defineLambdaForExecutingSql(role);
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

        sqlExecutor = JavaLambda.builder()
                .name(instance.getName() + "-" + RESOURCE_NAME)
                .handler(SqlExecutor.class)
                .artifactName(SQL_EXECUTOR_ARTIFACT_NAME)
                .role(lambdaRole)
                .environment(instance.getRootUserConnectionDetails().applyValue(
                        connectionDetails -> FunctionEnvironmentArgs.builder()
                                .variables(Map.of("DB_URL", connectionDetails.url(),
                                        "DB_USERNAME", instance.getRootUsername(),
                                        "DB_PORT", connectionDetails.port(),
                                        SECRET_NAME_ENV_VARIABLE, connectionDetails.secretName()))
                                .build()))
                .vpcConfig(FunctionVpcConfigArgs.builder()
                        .subnetIds(subnetIds)
                        .securityGroupIds(securityGroupIds)
                        .build())
                .build()
                .define();
    }

    private Role defineRoleForLambda() {

        var allowConnectToDatabasePolicy = instance.createPolicyToGetRootUserSecret(RESOURCE_NAME);

        var assumeLambdaRole = IamPolicyFunctions.createAssumeRolePolicyDocument("lambda.amazonaws.com");

        return new Role("initializer-lambda-role-" + instance.getName(), new RoleArgs.Builder()
                .assumeRolePolicy(assumeLambdaRole.applyValue(GetPolicyDocumentResult::json))
                .managedPolicyArns(allowConnectToDatabasePolicy.arn().applyValue(rdsPolicyArn -> List.of(
                        ManagedPolicies.LambdaVpcAccessExecution.getArn(),
                        rdsPolicyArn)))
                .build());
    }

    public void execute(String logicalName, InputStream resourceContainingSql) {

        try {

            var sqlString = IOUtils.toString(resourceContainingSql, Charsets.UTF_8);

            execute(logicalName, sqlString);

        } catch (IOException e) {

            throw new RuntimeException(e);
        }
    }

    public void execute(String logicalName, String sqlString) {

        execute(logicalName, Output.of(sqlString));
    }

    public void execute(String logicalName, Output<String> sqlString) {


        var input = sqlString.applyValue(s ->  {

            var gson = new Gson();
            var object = new JsonObject();
            object.add("sql", new JsonPrimitive(s));
            return gson.toJson(object);
        });

        var executionsPreviousToThisOne = new ArrayList<>(previousExecutions);


        var thisExecution = new Invocation(instance.getName() + "-" + logicalName, InvocationArgs.builder()
                .functionName(sqlExecutor.name())
                .input(input)
                .build(), CustomResourceOptions.builder().dependsOn(executionsPreviousToThisOne).build()
        );

        previousExecutions.add(thisExecution);
    }
}
