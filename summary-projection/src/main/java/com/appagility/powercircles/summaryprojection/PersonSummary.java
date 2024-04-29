package com.appagility.powercircles.summaryprojection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonSummary {

    private String personId;
    private String name;
}
