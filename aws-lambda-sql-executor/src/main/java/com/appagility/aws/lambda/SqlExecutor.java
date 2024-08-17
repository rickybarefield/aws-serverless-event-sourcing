package com.appagility.aws.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.appagility.powercircles.connectionfactories.RdsPostgresConnectionFactory;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.sql.SQLException;

public class SqlExecutor implements RequestHandler<SqlExecutorEvent, String> {

    private static final Logger LOG = LoggerFactory.getLogger(SqlExecutor.class);

    @Override
    public String handleRequest(SqlExecutorEvent sqlExecutorEvent, Context context) {

        LOG.warn("Invoked with " + sqlExecutorEvent);

        try (var connection = new RdsPostgresConnectionFactory().create()) {

            ScriptRunner runner = new ScriptRunner(connection);
            runner.setSendFullScript(false);
            runner.setStopOnError(true);
            runner.runScript(new StringReader(sqlExecutorEvent.sql()));

            return "Script completed";

        } catch (SQLException e) {

            throw new RuntimeException(e);
        }
    }
}
