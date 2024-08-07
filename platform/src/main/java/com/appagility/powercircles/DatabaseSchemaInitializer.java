package com.appagility.powercircles;

import com.amazonaws.auth.policy.Statement;
import com.appagility.aws.lambda.SqlExecutor;
import com.pulumi.asset.FileArchive;
import com.pulumi.aws.AwsFunctions;
import com.pulumi.aws.iam.IamFunctions;
import com.pulumi.aws.iam.Role;
import com.pulumi.aws.iam.RoleArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementArgs;
import com.pulumi.aws.iam.outputs.GetPolicyDocumentResult;
import com.pulumi.aws.lambda.Function;
import com.pulumi.aws.lambda.FunctionArgs;
import com.pulumi.aws.lambda.enums.Runtime;
import com.pulumi.aws.lambda.inputs.FunctionEnvironmentArgs;
import com.pulumi.aws.outputs.GetCallerIdentityResult;
import com.pulumi.aws.outputs.GetRegionResult;
import com.pulumi.aws.rds.Instance;
import com.pulumi.aws.rds.Proxy;
import com.pulumi.aws.rds.ProxyArgs;
import com.pulumi.aws.rds.inputs.ProxyAuthArgs;
import com.pulumi.core.Output;
import lombok.Builder;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Builder
public class DatabaseSchemaInitializer {

    private static final String SQL_EXECUTOR_ARTIFACT_NAME = "aws-lambda-sql-executor.jar";
    private static final Duration EXECUTOR_TIMEOUT = Duration.ofMinutes(2);

    private final String databaseName;
    private final String databaseUsername;
    private final Instance databaseInstance;

    public void defineInfrastructure() {

//        defineRdsProxy();
        defineLambdaForExecutingSql();
    }

    //TODO Not convinced we need a proxy for this use, we will for the actual projection lambda
    private void defineRdsProxy() {

        new Proxy(databaseName + "-proxy-sql-executor", ProxyArgs.builder()
                .engineFamily("postgres")
                .auths(List.of(ProxyAuthArgs.builder().build()))
                .build());
    }

    private void defineLambdaForExecutingSql() {

        var functionEnvArgs = FunctionEnvironmentVars.create(databaseInstance);

        var lambdaRole = defineRoleForLambda();

        var function = new Function(databaseName + "-schema-initializer", FunctionArgs
                .builder()
                .runtime(Runtime.Java21)
                .handler(SqlExecutor.class.getName())
                .role(lambdaRole.arn())
                .code(new FileArchive("./target/lambdas/" + SQL_EXECUTOR_ARTIFACT_NAME))
                .timeout((int) EXECUTOR_TIMEOUT.toSeconds())
                .environment(functionEnvArgs.applyValue(
                        args -> FunctionEnvironmentArgs.builder()
                                .variables(Map.of("DB_URL", args.url,
                                        "DB_USERNAME", databaseUsername,
                                        "DB_PORT", String.valueOf(args.port),
                                        "DB_HOSTNAME", args.hostname))
                                .build()))
                .build());

    }

    private Role defineRoleForLambda() {

        var regionAccountIdAndResourceIdOutputs = Output.all(
                AwsFunctions.getRegion().applyValue(GetRegionResult::name),
                AwsFunctions.getCallerIdentity().applyValue(GetCallerIdentityResult::accountId),
                databaseInstance.resourceId());

        var databaseAccountArnOutput = regionAccountIdAndResourceIdOutputs.applyValue(regionAccountIdAndResourceId ->
                String.format("arn:aws:rds-db:%s:%s:dbuser:%s/%s",
                        regionAccountIdAndResourceId.get(0),
                        regionAccountIdAndResourceId.get(1),
                        regionAccountIdAndResourceId.get(2),
                        databaseUsername)
                );

        var allowConnectToDatabase = IamFunctions.getPolicyDocument(GetPolicyDocumentArgs.builder()
                .statements(databaseAccountArnOutput.applyValue(
                        databaseAccountArn -> Collections.singletonList(GetPolicyDocumentStatementArgs.builder()
                        .effect(Statement.Effect.Allow.name())
                        .actions("rds-db:connect")
                        .resources(databaseAccountArn)
                        .build())))
                .build());

        var allowConnectToDatabasePolicy = IamPolicyFunctions.policyForDocument(allowConnectToDatabase,
                "allow_connect_to_" + databaseName);

        var assumeLambdaRole = IamPolicyFunctions.createAssumeRolePolicyDocument("lambda.amazonaws.com");

        return new Role("lambda-role-" + databaseName, new RoleArgs.Builder()
                .assumeRolePolicy(assumeLambdaRole.applyValue(GetPolicyDocumentResult::json))
                .managedPolicyArns(allowConnectToDatabasePolicy.arn().applyValue(rdsPolicyArn -> List.of(
                        "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole",
                        rdsPolicyArn)))
                .build());
    }

    private record FunctionEnvironmentVars(String hostname, String port, String url) {

        public static Output<FunctionEnvironmentVars> create(Instance databaseInstance) {

            return Output.all(databaseInstance.address(),
                            databaseInstance.port().applyValue(Object::toString),
                            databaseInstance.address())
                    .applyValue(addressPortAndAddress -> new FunctionEnvironmentVars(
                            addressPortAndAddress.get(0),
                            addressPortAndAddress.get(1),
                            createUrl(addressPortAndAddress)));
        }

        private static String createUrl(List<String> addressPortAndAddress) {

            return String.format("postgresql://%s:%s/%s",
                    addressPortAndAddress.get(0),
                    addressPortAndAddress.get(1),
                    addressPortAndAddress.get(2));
        }

    }
}
