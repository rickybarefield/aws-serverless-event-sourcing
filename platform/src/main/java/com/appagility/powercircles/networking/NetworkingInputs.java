package com.appagility.powercircles.networking;

import com.pulumi.Context;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import lombok.Getter;

public class NetworkingInputs {

    @Getter
    private final IPAddress networkCidrRange;

    public NetworkingInputs(IPAddress networkCidrRange) {

        this.networkCidrRange = networkCidrRange;
    }


    public static NetworkingInputs deserialize(Context context) {

        String networkCidrRangeString = context.config().require("networkCidrRange");

        return new NetworkingInputs(new IPAddressString(networkCidrRangeString).getAddress());
    }
}