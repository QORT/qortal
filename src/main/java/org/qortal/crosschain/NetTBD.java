package org.qortal.crosschain;

import org.bitcoinj.core.NetworkParameters;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

public class NetTBD {

    private String name;
    private AtomicLong feeRequired;
    private NetworkParameters params;
    private Collection<ElectrumX.Server> servers;
    private String genesisHash;

    public NetTBD(String name, long feeRequired, NetworkParameters params, Collection<ElectrumX.Server> servers, String genesisHash) {
        this.name = name;
        this.feeRequired = new AtomicLong(feeRequired);
        this.params = params;
        this.servers = servers;
        this.genesisHash = genesisHash;
    }

    public String getName() {

        return this.name;
    }

    public long getFeeRequired() {

        return feeRequired.get();
    }

    public void setFeeRequired(long feeRequired) {

        this.feeRequired.set(feeRequired);
    }

    public NetworkParameters getParams() {

        return this.params;
    }

    public Collection<ElectrumX.Server> getServers() {

        return this.servers;
    }

    public String getGenesisHash() {

        return this.genesisHash;
    }
}