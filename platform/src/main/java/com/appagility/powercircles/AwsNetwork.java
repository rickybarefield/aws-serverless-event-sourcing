package com.appagility.powercircles;

import com.google.common.collect.Streams;
import com.pulumi.aws.AwsFunctions;
import com.pulumi.aws.ec2.SecurityGroup;
import com.pulumi.aws.ec2.Vpc;
import com.pulumi.aws.ec2.VpcArgs;
import com.pulumi.aws.inputs.GetAvailabilityZonesPlainArgs;
import com.pulumi.core.Output;
import com.pulumi.core.Tuples;
import inet.ipaddr.IPAddress;
import lombok.SneakyThrows;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AwsNetwork {

    private final String name;

    private final NamingStrategy namingStrategy;

    private final IPAddress range;

    private final Map<String, List<AwsSubnet>> subnetsByTier;
    private Vpc vpc;

    private Supplier<AwsVpcEndpoint> lazySecretsManagerEndpoint;

    public AwsNetwork(NamingStrategy namingStrategy, String name, IPAddress range, Map<String, List<AwsSubnet>> subnetsByTier) {

        this.namingStrategy = namingStrategy;
        this.name = name;
        this.range = range;
        this.subnetsByTier = subnetsByTier;

        this.lazySecretsManagerEndpoint = () -> AwsVpcEndpoint.define(namingStrategy, this, "secretsmanager");
    }

    public static AwsNetworkBuilder builder() {

        return new AwsNetworkBuilder();
    }

    public List<AwsSubnet> getSubnets(String tier) {

        return new ArrayList<>(subnetsByTier.get(tier));
    }

    public void defineInfrastructure() {

        vpc = new Vpc(namingStrategy.generateName(name), VpcArgs.builder()
                .cidrBlock(range.toString())
                .enableDnsHostnames(true)
                .enableDnsSupport(true)
                .build());

        allSubnets().forEach(s -> s.defineInfrastructure(vpc));
    }

    Stream<AwsSubnet> allSubnets() {

        return subnetsByTier.values().stream().flatMap(Collection::stream);
    }

    public Output<String> getVpcId() {

        return vpc.id();
    }


    public static class AwsNetworkBuilder {

        private NamingStrategy namingStrategy;
        private String name;
        private List<String> tierNames;

        private IPAddress networkRange;

        private AwsNetworkBuilder() {
        }

        public AwsNetworkBuilder namingStrategy(NamingStrategy namingStrategy) {

            this.namingStrategy = namingStrategy;

            return this;
        }

        public AwsNetworkBuilder name(String name) {

            this.name = name;

            return this;
        }

        public AwsNetworkBuilder tierNames(String... tierNames) {

            this.tierNames = Arrays.asList(tierNames);

            return this;
        }

        public AwsNetworkBuilder networkRange(IPAddress networkRange) {

            this.networkRange = networkRange;

            return this;
        }

        @SneakyThrows
        public AwsNetwork buildWithEvenlySplitSubnetsAcrossAllAvailabilityZones() {

            var availabilityZoneResult = AwsFunctions.getAvailabilityZonesPlain(GetAvailabilityZonesPlainArgs.builder()
                    .state("available").build()).get();

            var availabilityZones = availabilityZoneResult.names();

            var subnetsNeeded = tierNames.size() * availabilityZones.size();

            var subnetRanges = Subnetting.split(networkRange, subnetsNeeded);

            Stream<Tuples.Tuple2<String, String>> tierNamesWithAvailabilityZone =
                    tierNames.stream().flatMap(tierName -> availabilityZones.stream().map(az -> Tuples.of(tierName, az)));

            var subnets = Streams.zip(
                    tierNamesWithAvailabilityZone, subnetRanges.stream(),
                    (tierAndAz, subnetRange) -> new AwsSubnet(namingStrategy, tierAndAz.t1, subnetRange, tierAndAz.t2)).toList();

            Map<String, List<AwsSubnet>> subnetsByTierName = subnets.stream().collect(Collectors.groupingBy(AwsSubnet::getTierName));

            return new AwsNetwork(namingStrategy, name, networkRange, subnetsByTierName);
        }
    }

    public void allowAccessToSecretsManager(String forResource, SecurityGroup from) {

        lazySecretsManagerEndpoint.get().allowHttpsAccessFrom(forResource, from);
    }
}