package com.appagility.powercircles;

import lombok.Getter;

public enum ManagedPolicies {

    LambdaBasicExecution("arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"),
    LambdaSqaQueueExection("arn:aws:iam::aws:policy/service-role/AWSLambdaSQSQueueExecutionRole");
    @Getter
    private String arn;

    ManagedPolicies(String arn) {

        this.arn = arn;
    }


}
