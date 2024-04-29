package com.appagility.powercircles.summaryprojection;

import lombok.AllArgsConstructor;

import java.sql.SQLException;
import java.util.List;

@AllArgsConstructor
public class QueryHandler {

    private final PersonSummaryDao personSummaryDao;

    public List<PersonSummary> getAll() {

        try {

            return personSummaryDao.loadAll();

        } catch (SQLException e) {

            throw new RuntimeException(e);
        }
    }

}
