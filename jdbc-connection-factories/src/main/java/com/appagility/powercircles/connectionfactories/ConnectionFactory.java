package com.appagility.powercircles.connectionfactories;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public abstract class ConnectionFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionFactory.class);

    public final Connection create() {

        try {

            setSslProperties();

            Class.forName(getDriverName());

            LOG.warn("Connecting to " + getDbUrl());

            return DriverManager.getConnection(getDbUrl(), getDbConnectionProperties());

        } catch (SQLException | ClassNotFoundException e) {

            throw new RuntimeException(e);
        }
    }

    protected void setSslProperties() {
        //nop
    }

    //TODO Possibly no longer needed due to new Java Service Provider mechanism
    protected abstract String getDriverName();
    protected abstract String getDbUrl();
    protected abstract Properties getDbConnectionProperties();
}
