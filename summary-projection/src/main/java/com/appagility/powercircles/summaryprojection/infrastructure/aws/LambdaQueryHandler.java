package com.appagility.powercircles.summaryprojection.infrastructure.aws;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.appagility.powercircles.connectionfactories.RdsPostgresSecretAuthConnectionFactory;
import com.appagility.powercircles.summaryprojection.PersonSummaryDao;
import com.appagility.powercircles.summaryprojection.QueryHandler;
import com.appagility.powercircles.summaryprojection.SummaryProjection;

import java.util.List;

public class LambdaQueryHandler implements RequestHandler<Void, List<SummaryProjection>> {

    private QueryHandler queryHandler =
            new QueryHandler(new PersonSummaryDao(new RdsPostgresSecretAuthConnectionFactory()));

    @Override
    public List<SummaryProjection> handleRequest(Void unused, Context context) {

        return queryHandler.getAll();
    }
}
