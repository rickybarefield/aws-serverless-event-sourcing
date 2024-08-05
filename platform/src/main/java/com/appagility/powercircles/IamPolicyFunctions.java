package com.appagility.powercircles;

import com.amazonaws.auth.policy.Statement;
import com.pulumi.aws.iam.IamFunctions;
import com.pulumi.aws.iam.Policy;
import com.pulumi.aws.iam.PolicyArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementPrincipalArgs;
import com.pulumi.aws.iam.outputs.GetPolicyDocumentResult;
import com.pulumi.core.Output;

public final class IamPolicyFunctions {

    public static Output<GetPolicyDocumentResult> createAssumeRolePolicyDocument(String service) {

        return IamFunctions.getPolicyDocument(GetPolicyDocumentArgs.builder()
                .statements(GetPolicyDocumentStatementArgs.builder()
                        .effect(Statement.Effect.Allow.name())
                        .actions("sts:AssumeRole")
                        .principals(GetPolicyDocumentStatementPrincipalArgs.builder()
                                .type("Service")
                                .identifiers(service)
                                .build())
                        .build())
                .build());
    }

    public static Policy policyForDocument(Output<GetPolicyDocumentResult> policyDocument, String policyName) {

        return new Policy(policyName, new PolicyArgs.Builder()
                .policy(policyDocument.applyValue(GetPolicyDocumentResult::json))
                .build());
    }

}
