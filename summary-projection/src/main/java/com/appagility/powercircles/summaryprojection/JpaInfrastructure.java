package com.appagility.powercircles.summaryprojection;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Persistence;

public class JpaInfrastructure {

    public static EntityManager createEntityManager() {

        var entityManagerFactory = Persistence.createEntityManagerFactory("summary-projection");

        EntityManager entityManager = entityManagerFactory.createEntityManager();
        return entityManager;
    }

}
