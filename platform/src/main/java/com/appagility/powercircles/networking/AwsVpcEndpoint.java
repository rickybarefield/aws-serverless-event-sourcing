package com.appagility.powercircles.networking;

import com.appagility.powercircles.NamingStrategy;
import com.pulumi.aws.AwsFunctions;
import com.pulumi.aws.ec2.*;
import com.pulumi.aws.ec2.enums.ProtocolType;
import com.pulumi.aws.outputs.GetRegionResult;
import com.pulumi.core.Output;

import java.util.Collections;

public class AwsVpcEndpoint {

    private final NamingStrategy namingStrategy;
    private final AwsNetwork awsNetwork;
    private final String service;

    private VpcEndpoint endpoint;

    private SecurityGroup securityGroup;

    private AwsVpcEndpoint(NamingStrategy namingStrategy, AwsNetwork awsNetwork, String service) {

        this.namingStrategy = namingStrategy;
        this.awsNetwork = awsNetwork;
        this.service = service;
    }

    public static AwsVpcEndpoint define(NamingStrategy namingStrategy, AwsNetwork awsNetwork, String service) {

        var endpoint = new AwsVpcEndpoint(namingStrategy, awsNetwork, service);

        endpoint.defineInfrastructure();

        return endpoint;
    }

    private void defineInfrastructure() {

        securityGroup = new SecurityGroup(namingStrategy.generateName("endpoint", service), SecurityGroupArgs.builder()
                .vpcId(awsNetwork.getVpcId())
                .build());

        var region = AwsFunctions.getRegion().applyValue(GetRegionResult::name);

        var serviceName = region.applyValue(r -> String.format("com.amazonaws.%s.%s", r, service));

        var subnetIds = Output.all(awsNetwork.allSubnets().map(AwsSubnet::getId).toList());

        endpoint = new VpcEndpoint(namingStrategy.generateName("endpoint", service), VpcEndpointArgs.builder()
                .vpcEndpointType("Interface")
                .vpcId(awsNetwork.getVpcId())
                .serviceName(serviceName)
                .privateDnsEnabled(true)
                .subnetIds(subnetIds)
                .securityGroupIds(securityGroup.id().applyValue(Collections::singletonList))
                .build());
    }

    public void allowHttpsAccessFrom(String resource, SecurityGroup other) {

        int port = 443;

        new SecurityGroupRule(namingStrategy.generateName(resource, "egress", "to", service), SecurityGroupRuleArgs.builder()
                .type("egress")
                .securityGroupId(other.id())
                .sourceSecurityGroupId(securityGroup.id())
                .protocol(ProtocolType.TCP)
                .toPort(port)
                .fromPort(port)
                .build());

        new SecurityGroupRule(namingStrategy.generateName(service, "ingress", "from", resource), SecurityGroupRuleArgs.builder()
                .type("ingress")
                .securityGroupId(securityGroup.id())
                .sourceSecurityGroupId(other.id())
                .protocol(ProtocolType.TCP)
                .toPort(port)
                .fromPort(port)
                .build());
    }
}
