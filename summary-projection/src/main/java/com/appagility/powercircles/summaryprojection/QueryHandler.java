package com.appagility.powercircles.summaryprojection;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Persistence;

import java.util.List;

public class QueryHandler {

    public List<PersonSummary> getAll() {

        try(EntityManager entityManager =  JpaInfrastructure.createEntityManager()) {

            var query = entityManager.createQuery("SELECT s FROM PersonSummary s", PersonSummary.class);
            return query.getResultList();
        }

    }

}
