package com.appagility.powercircles.summaryprojection;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;

@Builder
@Entity
@Table(name = "PersonSummary")
public class PersonSummary {

    @Id
    private String id;
    private String name;
}
