package com.appagility.powercircles.connectionfactories;

import software.amazon.awssdk.regions.Region;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Properties;

public abstract class RdsPostgresConnectionFactory extends ConnectionFactory {


    private static final String DB_USERNAME_ENV_VARIABLE = "DB_USERNAME";
    private static final String DB_URL_ENV_VARIABLE = "DB_URL";
    private static final String SSL_CERTIFICATE = "eu-west-1-bundle.pem";
    private static final String KEY_STORE_TYPE = "JKS";
    private static final String KEY_STORE_PROVIDER = "SUN";
    private static final String KEY_STORE_FILE_PREFIX = "sys-connect-via-ssl-test-cacerts";
    private static final String KEY_STORE_FILE_SUFFIX = ".jks";
    private static final String DEFAULT_KEY_STORE_PASSWORD = "changeit";


    @Override
    protected final String getDriverName() {

        return "org.postgresql.Driver";
    }

    @Override
    protected final String getDbUrl() {

        return System.getenv(DB_URL_ENV_VARIABLE);
    }

    @Override
    protected final Properties getDbConnectionProperties() {

        Properties properties = new Properties();
        properties.setProperty("sslmode", "require");
        properties.setProperty("user", getDbUsername());
        properties.setProperty("password", getDbPassword());
        return properties;
    }

    protected abstract String getDbPassword();


    protected String getDbUsername() {

        return System.getenv(DB_USERNAME_ENV_VARIABLE);
    }


    @Override
    protected final void setSslProperties() {

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

    protected Region getRegion() {

        var awsRegion = System.getenv("AWS_REGION");
        var awsDefaultRegion = System.getenv("AWS_DEFAULT_REGION");

        return Region.of(awsRegion == null ? awsDefaultRegion : awsRegion);
    }

}
