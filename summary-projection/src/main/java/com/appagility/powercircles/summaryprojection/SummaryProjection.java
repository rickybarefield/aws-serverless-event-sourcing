package com.appagility.powercircles.summaryprojection;

public record SummaryProjection(String personId, String name) {

    static SummaryProjection from(PersonSummary personSummary) {

        return new SummaryProjection(personSummary.getPersonId(), personSummary.getName());
    }

}
