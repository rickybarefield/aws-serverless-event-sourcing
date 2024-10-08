package com.appagility.powercircles.wrappers;

import com.appagility.powercircles.common.MayBecome;
import com.appagility.powercircles.common.NamingContext;
import com.appagility.powercircles.networking.AwsNetwork;
import com.appagility.powercircles.networking.AwsSubnet;
import com.pulumi.aws.ec2.SecurityGroup;
import com.pulumi.aws.ec2.SecurityGroupArgs;
import com.pulumi.aws.ec2.SecurityGroupRule;
import com.pulumi.aws.ec2.SecurityGroupRuleArgs;
import com.pulumi.aws.iam.Policy;
import com.pulumi.aws.rds.Instance;
import com.pulumi.aws.rds.InstanceArgs;
import com.pulumi.aws.rds.SubnetGroup;
import com.pulumi.aws.rds.SubnetGroupArgs;
import com.pulumi.core.Output;
import lombok.Builder;
import lombok.Getter;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public class AwsRdsInstance {

    private final AwsNetwork awsNetwork;
    private final List<AwsSubnet> awsSubnets;

    @Getter
    private final String name;
    private final String username;

    private MayBecome<Instance> instance = MayBecome.empty("instance");

    private RdsUser rootUser;

    private MayBecome<SecurityGroup> securityGroup = MayBecome.empty("securityGroup");
    private DatabaseSqlExecutor databaseSqlExecutor;

    @Builder
    public AwsRdsInstance(AwsNetwork awsNetwork, List<AwsSubnet> awsSubnets, String name, String username) {

        this.awsNetwork = awsNetwork;
        this.awsSubnets = awsSubnets;
        this.name = name;
        this.username = username;
    }

    public void defineInfrastructure(NamingContext parentNamingContext) {

        var namingContext = parentNamingContext.with(name);

        this.rootUser = new RdsUser(namingContext, username);
        defineSecurityGroup(namingContext);
        rootUser.defineSecret();
        defineDatabase();
        defineSqlExecutor(namingContext);
    }

    private void defineDatabase() {


        var subnetIds = Output.all(awsSubnets.stream().map(AwsSubnet::getId).toList());

        var subnetGroup = new SubnetGroup(name + "-projections", SubnetGroupArgs.builder()
                .subnetIds(subnetIds)
                .build());

        var securityGroupIds = securityGroup.get().id().applyValue(Collections::singletonList);

        instance.set(new Instance(name + "-projections", InstanceArgs.builder()
                .dbName(getDbName())
                .username(rootUser.getUserName())
                .password(rootUser.getUserPassword())
                .allocatedStorage(5)
                .engine("postgres")
                .engineVersion("16.1")
                .instanceClass("db.t4g.micro")
                .skipFinalSnapshot(true)
                .publiclyAccessible(false)
                .deletionProtection(false)
                .iamDatabaseAuthenticationEnabled(false)
                .dbSubnetGroupName(subnetGroup.name())
                .vpcSecurityGroupIds(securityGroupIds)
                .build()));
    }

    private String getDbName() {

        return name + "Projections";
    }

    private void defineSecurityGroup(NamingContext namingContext) {

        var vpcId = awsSubnets.get(0).getVpcId();

        securityGroup.set(new SecurityGroup(namingContext.getName(), SecurityGroupArgs.builder()
                .vpcId(vpcId)
                .build()));
    }

    private void defineSqlExecutor(NamingContext namingContext) {

        databaseSqlExecutor = DatabaseSqlExecutor.builder()
                .instance(this)
                .awsNetwork(awsNetwork)
                .dataSubnets(awsSubnets)
                .build();

        databaseSqlExecutor.defineInfrastructure(namingContext);
    }

    public Output<String> getAddress() {

        return instance.get().address();
    }

    public Output<Integer> getPort() {

        return instance.get().port();
    }

    public void allowAccessFrom(NamingContext namingContext, SecurityGroup otherSecurityGroup) {

        new SecurityGroupRule(namingContext.with("to").with(name).getName(), SecurityGroupRuleArgs.builder()
                .fromPort(getPort())
                .toPort(getPort())
                .protocol("tcp")
                .type("egress")
                .sourceSecurityGroupId(securityGroup.get().id())
                .securityGroupId(otherSecurityGroup.id())
                .build()
        );

        new SecurityGroupRule(namingContext.with("from").with(name).getName(), SecurityGroupRuleArgs.builder()
                .fromPort(getPort())
                .toPort(getPort())
                .protocol("tcp")
                .type("ingress")
                .sourceSecurityGroupId(otherSecurityGroup.id())
                .securityGroupId(securityGroup.get().id())
                .build()
        );

    }

    public Policy createPolicyToGetRootUserSecret(NamingContext namingContext) {

        return rootUser.createPolicyToGetSecret(namingContext.with("root"));
    }

    public Output<ConnectionDetails> getRootUserConnectionDetails() {

        return ConnectionDetails.create(this, rootUser);
    }

    public Output<ConnectionDetails> getConnectionDetails(RdsUser rdsUser) {

        return ConnectionDetails.create(this, rdsUser);
    }

    public void executeAsSql(NamingContext namingContext, InputStream resourceContainingSql) {

        databaseSqlExecutor.execute(namingContext, resourceContainingSql);
    }

    public Output<String> getResourceId() {

        return instance.get().resourceId();
    }

    public String getRootUsername() {

        return rootUser.getUserName();
    }

    public RdsUser createUserAndGrantAllPermissionsOnSchema(NamingContext namingContext, String username, String schemaName) {

        var user = new RdsUser(namingContext, username);
        user.defineSecret();
        user.addUserToDatabase(databaseSqlExecutor);
        user.grantAllPermissionsOnSchema(databaseSqlExecutor, schemaName);
        return user;
    }

    public record ConnectionDetails(String hostname, String port, String url, String username, String secretName) {

        private static Output<ConnectionDetails> create(AwsRdsInstance databaseInstance, RdsUser user) {

            return Output.all(databaseInstance.getAddress(),
                            databaseInstance.getPort().applyValue(Object::toString),
                            user.getSecret().name())
                    .applyValue(addressPortAndSecretName -> new ConnectionDetails(
                            addressPortAndSecretName.get(0),
                            addressPortAndSecretName.get(1),
                            createUrl(addressPortAndSecretName.get(0), addressPortAndSecretName.get(1), databaseInstance.getDbName()),
                            user.getUserName(),
                            addressPortAndSecretName.get(2)
                            ));
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
