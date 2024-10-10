package com.appagility.powercircles.networking;

import com.appagility.powercircles.common.NamingContext;
import com.pulumi.aws.AwsFunctions;
import com.pulumi.aws.ec2.*;
import com.pulumi.aws.ec2.enums.ProtocolType;
import com.pulumi.aws.outputs.GetRegionResult;
import com.pulumi.core.Output;

import java.util.Collections;

public class AwsVpcEndpoint {

    private final NamingContext namingContext;
    private final AwsNetwork awsNetwork;
    private final String service;

    private VpcEndpoint endpoint;

    private SecurityGroup securityGroup;

    private AwsVpcEndpoint(NamingContext parentNamingContext, AwsNetwork awsNetwork, String service) {

        this.namingContext = parentNamingContext.with(service);
        this.awsNetwork = awsNetwork;
        this.service = service;
    }

    public static AwsVpcEndpoint define(NamingContext parentNamingContext, AwsNetwork awsNetwork, String service) {

        var endpoint = new AwsVpcEndpoint(parentNamingContext, awsNetwork, service);

        endpoint.defineInfrastructure();

        return endpoint;
    }

    private void defineInfrastructure() {

        securityGroup = new SecurityGroup(namingContext.getName(), SecurityGroupArgs.builder()
                .vpcId(awsNetwork.getVpcId())
                .build());

        var region = AwsFunctions.getRegion().applyValue(GetRegionResult::name);

        var serviceName = region.applyValue(r -> String.format("com.amazonaws.%s.%s", r, service));

        var subnetIds = Output.all(awsNetwork.allSubnets().map(AwsSubnet::getId).toList());

        endpoint = new VpcEndpoint(namingContext.getName(), VpcEndpointArgs.builder()
                .vpcEndpointType("Interface")
                .vpcId(awsNetwork.getVpcId())
                .serviceName(serviceName)
                .privateDnsEnabled(true)
                .subnetIds(subnetIds)
                .securityGroupIds(securityGroup.id().applyValue(Collections::singletonList))
                .build());
    }

    public void allowHttpsAccessFrom(NamingContext namingContext, SecurityGroup other) {

        int port = 443;

        new SecurityGroupRule(namingContext.with("egress").with("to").with(service).getName(), SecurityGroupRuleArgs.builder()
                .type("egress")
                .securityGroupId(other.id())
                .sourceSecurityGroupId(securityGroup.id())
                .protocol(ProtocolType.TCP)
                .toPort(port)
                .fromPort(port)
                .build());

        var distinctPart = namingContext.distinctName(this.namingContext);

        new SecurityGroupRule(namingContext.with(service).with("ingress").with("from").with(distinctPart).getName(), SecurityGroupRuleArgs.builder()
                .type("ingress")
                .securityGroupId(securityGroup.id())
                .sourceSecurityGroupId(other.id())
                .protocol(ProtocolType.TCP)
                .toPort(port)
                .fromPort(port)
                .build());
    }
}
