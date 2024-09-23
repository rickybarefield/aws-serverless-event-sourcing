package com.appagility.powercircles.common;

public interface NamingStrategy {

    String generateName(String resourceSpecificContext);

    default String generateName(String... resourceSpecificContexts) {

        return generateName(String.join("-", resourceSpecificContexts));
    }
}