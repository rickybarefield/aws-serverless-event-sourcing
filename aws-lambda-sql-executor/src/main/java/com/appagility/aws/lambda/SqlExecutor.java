package com.appagility.aws.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.appagility.powercircles.connectionfactories.RdsPostgresConnectionFactory;
import org.apache.ibatis.jdbc.ScriptRunner;

import java.io.StringReader;
import java.sql.SQLException;

public class SqlExecutor implements RequestHandler<SqlExecutorEvent, String> {

    @Override
    public String handleRequest(SqlExecutorEvent sqlExecutorEvent, Context context) {

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
