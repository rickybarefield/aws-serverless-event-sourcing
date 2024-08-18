package com.appagility.powercircles;

public interface NamingStrategy {

    String generateName(String resourceSpecificContext);

    default String generateName(String... resourceSpecificContexts) {

        return generateName(String.join("-", resourceSpecificContexts));
    }
}