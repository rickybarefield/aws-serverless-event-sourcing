package com.appagility.powercircles;

import com.amazonaws.auth.policy.Statement;
import com.pulumi.aws.AwsFunctions;
import com.pulumi.aws.ec2.SecurityGroup;
import com.pulumi.aws.ec2.SecurityGroupArgs;
import com.pulumi.aws.ec2.SecurityGroupRule;
import com.pulumi.aws.ec2.SecurityGroupRuleArgs;
import com.pulumi.aws.iam.IamFunctions;
import com.pulumi.aws.iam.Policy;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentArgs;
import com.pulumi.aws.iam.inputs.GetPolicyDocumentStatementArgs;
import com.pulumi.aws.outputs.GetCallerIdentityResult;
import com.pulumi.aws.outputs.GetRegionResult;
import com.pulumi.aws.rds.Instance;
import com.pulumi.aws.rds.InstanceArgs;
import com.pulumi.aws.rds.SubnetGroup;
import com.pulumi.aws.rds.SubnetGroupArgs;
import com.pulumi.core.Output;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

public class AwsRdsInstance {

    private final List<AwsSubnet> awsSubnets;

    @Getter
    private final String name;
    private MayBecome<Instance> instance = MayBecome.empty("instance");

    @Getter
    private final String username;

    private MayBecome<SecurityGroup> securityGroup = MayBecome.empty("securityGroup");
    private DatabaseSchemaInitializer databaseSchemaInitializer;

    @Builder
    public AwsRdsInstance(List<AwsSubnet> awsSubnets, String name, String username) {

        this.awsSubnets = awsSubnets;
        this.name = name;
        this.username = username;
    }

    public void defineInfrastructure() {

        defineSecurityGroup();
        defineDatabase();
        defineInitializer();
    }

    private void defineDatabase() {


        var subnetIds = Output.all(awsSubnets.stream().map(s -> s.getId()).toList());

        var subnetGroup = new SubnetGroup(name + "-projections", SubnetGroupArgs.builder()
                .subnetIds(subnetIds)
                .build());

        var securityGroupIds = securityGroup.get().id().applyValue(Collections::singletonList);

        instance.set(new Instance(name + "-projections", InstanceArgs.builder()
                .dbName(getDbName())
                .username(username)
                .password("FIXME_FIXME") //FIXME
                .allocatedStorage(5)
                .engine("postgres")
                .engineVersion("16.1")
                .instanceClass("db.t4g.micro")
                .skipFinalSnapshot(true)
                .publiclyAccessible(false)
                .deletionProtection(false)
                .iamDatabaseAuthenticationEnabled(true)
                .dbSubnetGroupName(subnetGroup.name())
                .vpcSecurityGroupIds(securityGroupIds)
                .build()));
    }

    private String getDbName() {

        return name + "Projections";
    }

    private void defineSecurityGroup() {

        var vpcId = awsSubnets.get(0).getVpcId();

        securityGroup.set(new SecurityGroup(name + "-projections", SecurityGroupArgs.builder()
                .vpcId(vpcId)
                .build()));
    }

    private void defineInitializer() {

        databaseSchemaInitializer = DatabaseSchemaInitializer.builder()
                .instance(this)
                .dataSubnets(awsSubnets)
                .build();

        databaseSchemaInitializer.defineInfrastructure();
    }

    public Output<String> getAddress() {

        return instance.get().address();
    }

    public Output<Integer> getPort() {

        return instance.get().port();
    }

    public void allowAccessFrom(String resourceName, SecurityGroup otherSecurityGroup) {

        new SecurityGroupRule(resourceName + "-to-" + name + "-egress", SecurityGroupRuleArgs.builder()
                .fromPort(getPort())
                .toPort(getPort())
                .protocol("tcp")
                .type("egress")
                .sourceSecurityGroupId(securityGroup.get().id())
                .securityGroupId(otherSecurityGroup.id())
                .build()
        );

        new SecurityGroupRule(name + "-from-" + resourceName + "-ingress", SecurityGroupRuleArgs.builder()
                .fromPort(getPort())
                .toPort(getPort())
                .protocol("tcp")
                .type("ingress")
                .sourceSecurityGroupId(otherSecurityGroup.id())
                .securityGroupId(securityGroup.get().id())
                .build()
        );

    }

    public Policy createPolicyToConnect(String resourceName) {

        var regionAccountIdAndResourceIdOutputs = Output.all(
                AwsFunctions.getRegion().applyValue(GetRegionResult::name),
                AwsFunctions.getCallerIdentity().applyValue(GetCallerIdentityResult::accountId),
                instance.get().resourceId());

        var databaseAccountArnOutput = regionAccountIdAndResourceIdOutputs.applyValue(regionAccountIdAndResourceId ->
                String.format("arn:aws:rds-db:%s:%s:dbuser:%s/%s",
                        regionAccountIdAndResourceId.get(0),
                        regionAccountIdAndResourceId.get(1),
                        regionAccountIdAndResourceId.get(2),
                        username)
        );

        var allowConnectToDatabase = IamFunctions.getPolicyDocument(GetPolicyDocumentArgs.builder()
                .statements(databaseAccountArnOutput.applyValue(
                        databaseAccountArn -> Collections.singletonList(GetPolicyDocumentStatementArgs.builder()
                                .effect(Statement.Effect.Allow.name())
                                .actions("rds-db:connect")
                                .resources(databaseAccountArn)
                                .build())))
                .build());

        return IamPolicyFunctions.policyForDocument(allowConnectToDatabase,
                resourceName + "-connect-to-" + name);

    }

    public Output<ConnectionDetails> getConnectionDetails() {

        return ConnectionDetails.create(this);
    }

    public record ConnectionDetails(String hostname, String port, String url) {

        private static Output<ConnectionDetails> create(AwsRdsInstance databaseInstance) {

            return Output.all(databaseInstance.getAddress(),
                            databaseInstance.getPort().applyValue(Object::toString))
                    .applyValue(addressAndPort -> new ConnectionDetails(
                            addressAndPort.get(0),
                            addressAndPort.get(1),
                            createUrl(addressAndPort.get(0), addressAndPort.get(1), databaseInstance.getDbName())));
        }

        private static String createUrl(String address, String port, String dbName) {

            return String.format("jdbc:postgresql://%s:%s/%s",
                    address,
                    port,
                    dbName
            );
        }

    }

}
