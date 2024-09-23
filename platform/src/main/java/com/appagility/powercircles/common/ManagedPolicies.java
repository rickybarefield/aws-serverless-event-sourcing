package com.appagility.powercircles.common;

import lombok.Getter;

public enum ManagedPolicies {

    LambdaBasicExecution("arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"),
    LambdaVpcAccessExecution("arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"),
    LambdaSqaQueueExecution("arn:aws:iam::aws:policy/service-role/AWSLambdaSQSQueueExecutionRole");
    @Getter
    private String arn;

    ManagedPolicies(String arn) {

        this.arn = arn;
    }


}
