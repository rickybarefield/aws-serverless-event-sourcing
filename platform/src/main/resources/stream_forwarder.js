const { SNSClient, PublishCommand } = require("@aws-sdk/client-sns");
const { unmarshall } = require("@aws-sdk/util-dynamodb");

exports.handler = async (event) => {

    const client = new SNSClient();

    const domainEvent = event.Records[0].dynamodb.NewImage;

    console.log(domainEvent.id.S + "-" + domainEvent.sequenceNumber.N);

    const input = {
      TopicArn: process.env.TARGET_TOPIC_ARN,
      Message: JSON.stringify(unmarshall(domainEvent)),
      MessageGroupId: domainEvent.id.S,
      MessageDeduplicationId: domainEvent.id.S + "-" + domainEvent.sequenceNumber.N
    };

    const command = new PublishCommand(input);

    await client.send(command);

};