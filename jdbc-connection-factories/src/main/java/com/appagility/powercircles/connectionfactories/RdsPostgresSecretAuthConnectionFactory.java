package com.appagility.powercircles.connectionfactories;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsUtilities;
import software.amazon.awssdk.services.rds.model.GenerateAuthenticationTokenRequest;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Properties;

public class RdsPostgresSecretAuthConnectionFactory extends RdsPostgresConnectionFactory {

    public static final String SECRET_NAME_ENV_VARIABLE = "DB_ROOT_USER_SECRET";
    private static final Logger LOG = LoggerFactory.getLogger(RdsPostgresSecretAuthConnectionFactory.class);

    @Override
    protected String getDbPassword() {

        var secretName = System.getenv(SECRET_NAME_ENV_VARIABLE);

        try(var client = SecretsManagerClient.builder()
                .region(getRegion())
                .build()) {

            var secretValue = client.getSecretValue(GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build());

            LOG.warn("Secret values loaded: " + secretValue + " DELETE THIS LOG STATEMENT!!!");

            return secretValue.secretString();
        }
    }
}
