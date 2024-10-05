package com.appagility.powercircles.summaryprojection;

import lombok.AllArgsConstructor;

import java.sql.SQLException;
import java.util.List;

@AllArgsConstructor
public class QueryHandler {

    private final PersonSummaryDao personSummaryDao;

    public List<SummaryProjection> getAll() {

        try {

            return personSummaryDao.loadAll().stream().map(SummaryProjection::from).toList();

        } catch (SQLException e) {

            throw new RuntimeException(e);
        }
    }

}
