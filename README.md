# AWS Serverless CQRS + Event Sourcing

A PoC for building a Serverless architecture for CQRS + Event Sourcing on AWS using Pulumi

![Architecutre Diagram](architecture.svg)

## Commands

Commands are created by an Integration from the API Gateway, and handled by the Command Handler (Lambda) 
which writes events to an Event Store (DynamoDb).

## Events

As Events are created by the Command Handlers in DynamoDb, they are
streamed (DynamoDb streams) to an Event Bus (SNS), here they are routed
to a Projection Queue (SQS).  These events are picked up by the Projection
Handler (Lambda) which writes events to a Projection State Table (RDS).

## Queries

Queries are routed from the API Gateway to a Query Handler (Lambda) which reads
from the Projection State Table (RDS).

## Replay (new projections)

When a new Projection is created, it will need to process all relevant events
already in the Projection Store (DynamoDb).  To do this when a new projection
is created, the following sequence of events takes place:

1. The Projection Handler (Lambda), the Projection Queue (SQS) and the Projection Replay Queue (SQS) are created
2. Live events are routed from the Event Bus (SNS) to the Projection Queue
3. The Projection Replay Queue is routed to the Projection Handler (Lambda)
4. A Replay Step Function takes the necessary events from the Event Store (DynamoDb) and puts them in the Projection Replay Queue (SQS) possibly using a GSI
5. We wait for the Projection Replay Queue to empty
6. We now route the Projection Queue to the Projection Handler

