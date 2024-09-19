package com.appagility.powercircles.summaryprojection;

import com.appagility.powercircles.domain.events.PersonCreatedEvent;
import com.appagility.powercircles.domain.events.PersonEventVisitor;
import lombok.AllArgsConstructor;

import java.sql.SQLException;

@AllArgsConstructor
public class PersonSummaryEventHandler implements PersonEventVisitor {

    private final PersonSummaryDao personSummaryDao;

    public void visit(PersonCreatedEvent personCreatedEvent) {

            var summary = PersonSummary.builder()
                    .personId(personCreatedEvent.getId())
                    .name(personCreatedEvent.getDetail().getName())
                    .build();

        try {

            personSummaryDao.persist(summary);

        } catch (SQLException e) {

            throw new RuntimeException(e);
        }
    }
}
