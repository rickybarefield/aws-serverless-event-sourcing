package com.appagility.powercircles;

import com.pulumi.asset.FileArchive;
import com.pulumi.aws.apigateway.RestApi;
import com.pulumi.aws.dynamodb.Table;
import com.pulumi.aws.dynamodb.TableArgs;
import com.pulumi.aws.dynamodb.inputs.TableAttributeArgs;
import com.pulumi.aws.iam.*;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementPrincipalArgs;
import com.pulumi.aws.iam.outputs.GetPolicyDocumentResult;
import com.pulumi.aws.lambda.Function;
import com.pulumi.aws.lambda.FunctionArgs;
import com.pulumi.aws.lambda.Permission;
import com.pulumi.aws.lambda.PermissionArgs;
import com.pulumi.aws.lambda.inputs.FunctionEnvironmentArgs;
import com.pulumi.aws.sqs.Queue;
import com.pulumi.aws.sqs.QueueArgs;
import com.pulumi.core.Output;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Builder
public class Aggregate {

    public static final Duration LAMBDA_TIMEOUT = Duration.ofMinutes(1);
    private String name;

    @Singular
    @Getter
    private List<Command> commands;
    private String commandHandlerArtifactName;
    private String commandHandlerName;

    public void defineInfrastructure(RestApi restApi) {

        var table = defineDynamoTable();

        var commandHandlerQueue = defineQueueForCommandHandler();

        var lambdaRole = defineRoleForLambda(table);
        var commandHandler = defineLambda(table, lambdaRole, restApi);

        definePolicyAndAttachmentToReceiveFromSqs(commandHandlerQueue, lambdaRole);

        commands.forEach(c -> c.defineRouteAndConnectToCommandBus(restApi, commandHandler));
    }


    private Table defineDynamoTable() {

        return new Table(name, new TableArgs.Builder()
                .attributes(
                        new TableAttributeArgs.Builder().name("id").type("S").build(),
                        new TableAttributeArgs.Builder().name("sequenceNumber").type("N").build()
                )
                .hashKey("id")
                .rangeKey("sequenceNumber")
                .billingMode("PAY_PER_REQUEST")
                .build());
    }

    private Queue defineQueueForCommandHandler() {

        return new Queue(name + "-command-queue", new QueueArgs.Builder()
                .fifoQueue(true)
                .contentBasedDeduplication(true)
                .visibilityTimeoutSeconds((int) LAMBDA_TIMEOUT.toSeconds()).build());
    }

    private Function defineLambda(Table table, Role lambdaRole, RestApi restApi) {


        var function = new Function(name, FunctionArgs
                .builder()
                .runtime("java21")
                .handler(commandHandlerName)
                .role(lambdaRole.arn())
                .code(new FileArchive("./target/lambdas/" + commandHandlerArtifactName))
                .timeout((int) LAMBDA_TIMEOUT.toSeconds())
                .environment(table.name().applyValue(
                        tableName -> FunctionEnvironmentArgs.builder()
                                .variables(Map.of("PERSON_TABLE_NAME", tableName)).build()))
                .build());

        new Permission("allow_invoke_from_restapi", PermissionArgs.builder()
                .action("lambda:InvokeFunction")
                .function(function.name())
                .principal("apigateway.amazonaws.com")
                .sourceArn(restApi.executionArn().applyValue(arn -> arn + "/*/*"))
                .build());

        return function;
    }

    private static Role defineRoleForLambda(Table table) {

        var allowAllOnDynamoForTablePolicyDocument = IamFunctions.getPolicyDocument(GetPolicyDocumentArgs.builder()
                .statements(table.arn().applyValue(tableArn -> Collections.singletonList(GetPolicyDocumentStatementArgs.builder()
                        .effect("Allow")
                        .actions("dynamodb:*")
                        .resources(tableArn)
                        .build())))
                .build());

        var allowAllOnDynamoForTablePolicy = policyForDocument(allowAllOnDynamoForTablePolicyDocument,
                "allow_all_on_dynamodb");

        var assumeLambdaRole = createAssumeRolePolicyDocument("lambda.amazonaws.com");

        return new Role("lambda-role", new RoleArgs.Builder()
                .assumeRolePolicy(assumeLambdaRole.applyValue(GetPolicyDocumentResult::json))
                .managedPolicyArns(allowAllOnDynamoForTablePolicy.arn().applyValue(dynamoArn -> List.of(
                        "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole",
                        dynamoArn)))
                .build());
    }

    private static Output<GetPolicyDocumentResult> createAssumeRolePolicyDocument(String service) {

        return IamFunctions.getPolicyDocument(GetPolicyDocumentArgs.builder()
                .statements(GetPolicyDocumentStatementArgs.builder()
                        .effect("Allow")
                        .actions("sts:AssumeRole")
                        .principals(GetPolicyDocumentStatementPrincipalArgs.builder()
                                .type("Service")
                                .identifiers(service)
                                .build())
                        .build())
                .build());
    }

    private void definePolicyAndAttachmentToReceiveFromSqs(Queue commandHandlerQueue, Role lambdaRole) {

        var policyDocument = IamFunctions.getPolicyDocument(GetPolicyDocumentArgs.builder()
                .statements(GetPolicyDocumentStatementArgs.builder()
                        .effect("Allow")
                        .actions("sqs:ReceiveMessage",
                                "sqs:DeleteMessage",
                                "sqs:GetQueueAttributes")
                        .resources(commandHandlerQueue.arn().applyValue(List::of))
                        .build())

                .build());

        var allowReceiveFromSqsPolicy = policyForDocument(policyDocument, "receive_from_sqs");

        new RolePolicyAttachment("receive_from_sqs_attachment", new RolePolicyAttachmentArgs.Builder()
                .role(lambdaRole.name())
                .policyArn(allowReceiveFromSqsPolicy.arn())
                .build());
    }

    private static Policy policyForDocument(Output<GetPolicyDocumentResult> policyDocument, String policyName) {

        return new Policy(policyName, new PolicyArgs.Builder()
                .policy(policyDocument.applyValue(GetPolicyDocumentResult::json))
                .build());
    }
}
