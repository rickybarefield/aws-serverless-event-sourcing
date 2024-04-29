package com.appagility.powercircles.summaryprojection.infrastructure.aws;

import com.appagility.powercircles.summaryprojection.ConnectionFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsUtilities;
import software.amazon.awssdk.services.rds.model.GenerateAuthenticationTokenRequest;

public class RdsConnectionFactory extends ConnectionFactory {

    @Override
    protected String getDriverName() {

        return null;
    }

    @Override
    protected String getDbUrl() {

        return System.getenv("DB_URL");
    }

    @Override
    protected String getDbUsername() {

        return System.getenv("DB_URL");
    }

    @Override
    protected String getDbPassword() {

        return null;
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
        return Region.of(System.getProperty("aws.region"));
    }
}
