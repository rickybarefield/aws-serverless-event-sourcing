package com.appagility.powercircles.commandhandlers.infrastructure.aws;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Batch(@JsonProperty("Records") List<Record> records) {
}
