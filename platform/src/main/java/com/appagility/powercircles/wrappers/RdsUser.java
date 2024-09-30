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

import java.sql.PreparedStatement;
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

    public void grantAllPermissionsOnSchema(DatabaseSqlExecutor initializer, String schemaName) {

        initializer.execute(nameContext + "-allow-access-to-" + schemaName, createGrantSql(schemaName));
    }

    //A risk of SQL injection depending on usage, given expected usage probably okay but better to use
    //prepared statement approach, which may be difficuly without an actual SQL connection
    private String createGrantSql(String schemaName) {

        return "GRANT ALL ON ALL TABLES IN SCHEMA " + schemaName + " TO " + username + ";" +
                " GRANT USAGE ON SCHEMA " + schemaName + " TO " + username + ";" +
                " ALTER DEFAULT PRIVILEGES IN SCHEMA " + schemaName + " GRANT ALL ON TABLES TO " + username + ";";
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
