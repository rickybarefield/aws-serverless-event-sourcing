package com.appagility.powercircles.summaryprojection;

import lombok.AllArgsConstructor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
public class PersonSummaryDao {

    public static final String TABLE_NAME = "PersonSummary";
    private final ConnectionFactory connectionFactory;

    public void persist(PersonSummary personSummary) throws SQLException {

        var preparedStatement = connectionFactory.create().prepareStatement("INSERT INTO " + TABLE_NAME
                + " (personId, name)"
                + " VALUES (?, ?)");

        preparedStatement.setString(1, personSummary.getPersonId());
        preparedStatement.setString(2, personSummary.getName());

        preparedStatement.execute();
    }

    public List<PersonSummary> loadAll() throws SQLException {

        var statement = connectionFactory.create().createStatement();

        var resultSet = statement.executeQuery("SELECT personId, name FROM " + TABLE_NAME);

        var results = new ArrayList<PersonSummary>();

        while (resultSet.next()) {

            var personId = resultSet.getString("personId");
            var name = resultSet.getString("name");
            results.add(new PersonSummary(personId, name));
        }

        return results;
    }
}
