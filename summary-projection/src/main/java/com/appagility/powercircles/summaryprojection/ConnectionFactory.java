package com.appagility.powercircles.summaryprojection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public abstract class ConnectionFactory {

    public final Connection create() {

        try {

            Class.forName(getDriverName());
            return DriverManager.getConnection(getDbUrl(), getDbUsername(), getDbPassword());

        } catch (ClassNotFoundException | SQLException e) {

            throw new RuntimeException(e);
        }
    }

    protected abstract String getDriverName();
    protected abstract String getDbUrl();
    protected abstract String getDbUsername();
    protected abstract String getDbPassword();
}
