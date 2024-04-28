package com.appagility.powercircles.summaryprojection;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "PersonSummary")
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonSummary {

    @Id
    private String id;
    private String name;
}
