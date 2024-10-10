package com.appagility.powercircles.networking;

import com.appagility.powercircles.common.MayBecome;
import com.appagility.powercircles.common.NamingContext;
import com.pulumi.aws.ec2.Subnet;
import com.pulumi.aws.ec2.SubnetArgs;
import com.pulumi.aws.ec2.Vpc;
import com.pulumi.core.Output;
import inet.ipaddr.IPAddress;
import lombok.Getter;

public class AwsSubnet {

    @Getter
    private final IPAddress range;
    private final NamingContext namingContext;

    @Getter
    private final String availabilityZone;

    @Getter
    private final String tierName;

    private final MayBecome<Subnet> subnet = MayBecome.empty("subnet");

    public AwsSubnet(NamingContext parentNamingContext, String tierName, IPAddress range, String availabilityZone) {

        this.range = range;
        this.namingContext = parentNamingContext.with(tierName).with(availabilityZone);
        this.availabilityZone = availabilityZone;
        this.tierName = tierName;
    }

    public void defineInfrastructure(Vpc vpc) {

        subnet.set(new com.pulumi.aws.ec2.Subnet(namingContext.getName(),
                SubnetArgs.builder()
                        .vpcId(vpc.id())
                        .cidrBlock(range.toAddressString().toString())
                        .availabilityZone(availabilityZone).build()));
    }

    public Output<String> getId() {

        return subnet.get().id();
    }

    public Output<String> getVpcId() {

        return subnet.get().vpcId();
    }
}