package com.appagility.powercircles.connectionfactories;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsUtilities;
import software.amazon.awssdk.services.rds.model.GenerateAuthenticationTokenRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Properties;

public class RdsPostgresConnectionFactory extends ConnectionFactory {

    private static final Logger LOG = LoggerFactory.getLogger(RdsPostgresConnectionFactory.class);

    private static final String SSL_CERTIFICATE = "eu-west-1-bundle.pem";

    private static final String KEY_STORE_TYPE = "JKS";
    private static final String KEY_STORE_PROVIDER = "SUN";
    private static final String KEY_STORE_FILE_PREFIX = "sys-connect-via-ssl-test-cacerts";
    private static final String KEY_STORE_FILE_SUFFIX = ".jks";
    private static final String DEFAULT_KEY_STORE_PASSWORD = "changeit";


    @Override
    protected String getDriverName() {

        return "org.postgresql.Driver";
    }

    @Override
    protected String getDbUrl() {

        return System.getenv("DB_URL");
    }

    @Override
    protected Properties getDbConnectionProperties() {

        Properties properties = new Properties();
        properties.setProperty("sslmode", "require");
        properties.setProperty("user", getDbUsername());
        properties.setProperty("password",generateAuthToken());
        return properties;
    }


    private String getDbUsername() {

        return System.getenv("DB_USERNAME");
    }

    private String getDbPassword() {

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

        var token = client.generateAuthenticationToken(request);

        LOG.warn("Token generated: " + token);

        return token;
    }

    private static Region getRegion() {

        var awsRegion = System.getenv("AWS_REGION");
        var awsDefaultRegion = System.getenv("AWS_DEFAULT_REGION");

        return Region.of(awsRegion == null ? awsDefaultRegion : awsRegion);
    }

    @Override
    protected void setSslProperties() {

        System.setProperty("javax.net.ssl.trustStore", createKeyStoreFile());
        System.setProperty("javax.net.ssl.trustStoreType", KEY_STORE_TYPE);
        System.setProperty("javax.net.ssl.trustStorePassword", DEFAULT_KEY_STORE_PASSWORD);
    }

    private static String createKeyStoreFile() {

        try {

            return createKeyStoreFile(createCertificate()).getPath();

        } catch (Exception e) {

            throw new RuntimeException(e);
        }
    }

    private static X509Certificate createCertificate() throws Exception {

        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        URL url = new File(SSL_CERTIFICATE).toURI().toURL();
        if (url == null) {

            throw new Exception();
        }
        try (InputStream certInputStream = url.openStream()) {

            return (X509Certificate) certFactory.generateCertificate(certInputStream);
        }
    }

    private static File createKeyStoreFile(X509Certificate rootX509Certificate) throws Exception {

        File keyStoreFile = File.createTempFile(KEY_STORE_FILE_PREFIX, KEY_STORE_FILE_SUFFIX);

        try (FileOutputStream fos = new FileOutputStream(keyStoreFile.getPath())) {

            KeyStore ks = KeyStore.getInstance(KEY_STORE_TYPE, KEY_STORE_PROVIDER);
            ks.load(null);
            ks.setCertificateEntry("rootCaCertificate", rootX509Certificate);
            ks.store(fos, DEFAULT_KEY_STORE_PASSWORD.toCharArray());
        }
        return keyStoreFile;
    }
}
