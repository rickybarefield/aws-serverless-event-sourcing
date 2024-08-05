package com.appagility.powercircles.connectionfactories;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsUtilities;
import software.amazon.awssdk.services.rds.model.GenerateAuthenticationTokenRequest;

public class RdsPostgresConnectionFactory extends ConnectionFactory {

    @Override
    protected String getDriverName() {

        return "org.postgresql.Driver";
    }

    @Override
    protected String getDbUrl() {

        return System.getenv("DB_URL");
    }

    @Override
    protected String getDbUsername() {

        return System.getenv("DB_USERNAME");
    }

    @Override
    protected String getDbPassword() {

        return generateAuthToken();
    }

    private String generateAuthToken() {

        var request = GenerateAuthenticationTokenRequest.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .hostname(System.getenv("DB_HOSTNAME"))
                .region(getRegion())
                .username(getDbUsername())
                .port(Integer.valueOf(System.getenv("DB_PORT")))
                .build();

        var client = RdsUtilities.builder()
                .region(getRegion())
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        return client.generateAuthenticationToken(request);
    }

    private static Region getRegion() {

        var awsRegion = System.getenv("AWS_REGION");
        var awsDefaultRegion = System.getenv("AWS_DEFAULT_REGION");

        return Region.of(awsRegion == null ? awsDefaultRegion : awsRegion);
    }
}
