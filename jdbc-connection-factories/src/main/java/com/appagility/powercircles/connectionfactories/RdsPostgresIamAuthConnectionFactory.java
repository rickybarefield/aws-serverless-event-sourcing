package com.appagility.powercircles.connectionfactories;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsUtilities;
import software.amazon.awssdk.services.rds.model.GenerateAuthenticationTokenRequest;

public class RdsPostgresIamAuthConnectionFactory extends RdsPostgresConnectionFactory {

    private static final String DB_HOSTNAME_ENV_VARIABLE = "DB_HOSTNAME";
    private static final String DB_PORT_ENV_VARIABLE = "DB_PORT";
    private static final Logger LOG = LoggerFactory.getLogger(RdsPostgresConnectionFactory.class);

    @Override
    protected String getDbPassword() {

        var request = GenerateAuthenticationTokenRequest.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .hostname(System.getenv(DB_HOSTNAME_ENV_VARIABLE))
                .region(getRegion())
                .username(getDbUsername())
                .port(Integer.parseInt(System.getenv(DB_PORT_ENV_VARIABLE)))
                .build();

        var client = RdsUtilities.builder()
                .region(getRegion())
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        var token = client.generateAuthenticationToken(request);

        LOG.warn("Token generated: " + token);

        return token;
    }
}
