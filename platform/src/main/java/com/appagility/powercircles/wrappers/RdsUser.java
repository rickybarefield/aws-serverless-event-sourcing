package com.appagility.powercircles.wrappers;

import com.amazonaws.auth.policy.Statement;
import com.appagility.powercircles.common.IamPolicyFunctions;
import com.appagility.powercircles.common.MayBecome;
import com.pulumi.aws.iam.IamFunctions;
import com.pulumi.aws.iam.Policy;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementArgs;
import com.pulumi.aws.secretsmanager.Secret;
import com.pulumi.aws.secretsmanager.SecretVersion;
import com.pulumi.aws.secretsmanager.SecretVersionArgs;
import com.pulumi.core.Output;
import com.pulumi.random.RandomPassword;
import com.pulumi.random.RandomPasswordArgs;

import java.util.Collections;

public class RdsUser {

    private final String nameContext;
    private final String username;

    private MayBecome<Secret> userPasswordSecret = MayBecome.empty("userPasswordSecret");
    private MayBecome<RandomPassword> userPassword = MayBecome.empty("userPassword");

    public RdsUser(String nameContext, String username) {

        this.nameContext = nameContext;
        this.username = username;
    }

    public void defineSecret() {

        userPasswordSecret.set(new Secret(nameContext + "-projections-secret"));

        userPassword.set(new RandomPassword(nameContext + "-projections-user-password", RandomPasswordArgs.builder()
                .length(32)
                .lower(true)
                .upper(true)
                .numeric(true)
                .overrideSpecial(";<?=:[]{}|")
                .minSpecial(3)
                .build()));

        new SecretVersion(nameContext + "-projections-secret-version", SecretVersionArgs.builder()
                .secretId(userPasswordSecret.get().id())
                .secretString(userPassword.get().result())
                .build());
    }

    public void addUserToDatabase(DatabaseSqlExecutor initializer) {

        initializer.execute(nameContext + "-add-" + username,
                getUserPassword().applyValue(password -> "CREATE USER " + username + " WITH PASSWORD '" + password + "';"));
    }

    public Output<String> getUserPassword() {

        return userPassword.get().result();
    }


    public String getUserName() {

        return username;
    }

    public Secret getSecret() {

        return userPasswordSecret.get();
    }

    public Policy createPolicyToGetSecret(String resourceName) {

        var allowGetSecret = IamFunctions.getPolicyDocument(GetPolicyDocumentArgs.builder()
                .statements(GetPolicyDocumentStatementArgs.builder()
                                .effect(Statement.Effect.Allow.name())
                                .actions("secretsmanager:GetResourcePolicy",
                                        "secretsmanager:GetSecretValue",
                                        "secretsmanager:DescribeSecret",
                                        "secretsmanager:ListSecretVersionIds")
                                .resources(getSecret().arn().applyValue(Collections::singletonList))
                                .build(),
                        GetPolicyDocumentStatementArgs.builder()
                                .effect(Statement.Effect.Allow.name())
                                .actions("secretsmanager:ListSecrets")
                                .resources("*")
                                .build())
                .build());

        return IamPolicyFunctions.policyForDocument(allowGetSecret,
                resourceName + "-get-db-secret-" + username);

    }
}
