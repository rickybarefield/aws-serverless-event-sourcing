package com.appagility.powercircles.connectionfactories;

public class H2ConnectionFactory extends ConnectionFactory {

    public static final String DB_URL = "jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1";
    public static final String DB_USERNAME = "sa";
    public static final String DB_PASSWORD = "";

    @Override
    protected String getDriverName() {
        return "org.h2.Driver";
    }

    @Override
    protected String getDbUrl() {
        return DB_URL;
    }

    @Override
    protected String getDbUsername() {
        return DB_USERNAME;
    }
    @Override
    protected String getDbPassword() {
        return DB_PASSWORD;
    }
}
