package org.qortal.api.model.crosschain;

import org.qortal.crosschain.ServerInfo;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.Arrays;

@XmlAccessorType(XmlAccessType.FIELD)
public class BitcoinyTBDRequest {

    /**
     * Target Timespan
     *
     * extracted from /src/chainparams.cpp class
     * consensus.nPowTargetTimespan
     */
    private int targetTimespan;

    /**
     * Target Spacing
     *
     * extracted from /src/chainparams.cpp class
     * consensus.nPowTargetSpacing
     */
    private int targetSpacing;

    /**
     * Packet Magic
     *
     * extracted from /src/chainparams.cpp class
     * Concatenate the 4 values in pchMessageStart, then convert the hex to decimal.
     *
     * Ex. litecoin
     * pchMessageStart[0] = 0xfb;
     * pchMessageStart[1] = 0xc0;
     * pchMessageStart[2] = 0xb6;
     * pchMessageStart[3] = 0xdb;
     * packetMagic = 0xfbc0b6db = 4223710939
     */
    private long packetMagic;

    /**
     * Port
     *
     * extracted from /src/chainparams.cpp class
     * nDefaultPort
     */
    private int port;

    /**
     * Address Header
     *
     * extracted from /src/chainparams.cpp class
     * base58Prefixes[PUBKEY_ADDRESS] from Main Network
     */
    private int addressHeader;

    /**
     * P2sh Header
     *
     * extracted from /src/chainparams.cpp class
     * base58Prefixes[SCRIPT_ADDRESS] from Main Network
     */
    private int p2shHeader;

    /**
     * Segwit Address Hrp
     *
     * HRP -> Human Readable Parts
     *
     * extracted from /src/chainparams.cpp class
     * bech32_hrp
     */
    private String segwitAddressHrp;

    /**
     * Dumped Private Key Header
     *
     * extracted from /src/chainparams.cpp class
     * base58Prefixes[SECRET_KEY] from Main Network
     * This is usually, but not always ... addressHeader + 128
     */
    private int dumpedPrivateKeyHeader;

    /**
     * Subsidy Decreased Block Count
     *
     * extracted from /src/chainparams.cpp class
     * consensus.nSubsidyHalvingInterval
     *
     * Digibyte does not support this, because they do halving differently.
     */
    private int subsidyDecreaseBlockCount;

    /**
     * Expected Genesis Hash
     *
     * extracted from /src/chainparams.cpp class
     * consensus.hashGenesisBlock
     * Remove '0x' prefix
     */
    private String expectedGenesisHash;

    /**
     * Common Script Pub Key
     *
     * extracted from /src/chainparams.cpp class
     * This is the key commonly used to sign alerts for altcoins. Bitcoin and Digibyte are know exceptions.
     */
    public static final String SCRIPT_PUB_KEY = "040184710fa689ad5023690c80f3a49c8f13f8d45b8c857fbcbc8bc4a8e4d3eb4b10f4d4604fa08dce601aaf0f470216fe1b51850b4acf21b179c45070ac7b03a9";

    /**
     * The Script Pub Key
     *
     * extracted from /src/chainparams.cpp class
     * The key to sign alerts.
     *
     * const CScript genesisOutputScript = CScript() << ParseHex("040184710fa689ad5023690c80f3a49c8f13f8d45b8c857fbcbc8bc4a8e4d3eb4b10f4d4604fa08dce601aaf0f470216fe1b51850b4acf21b179c45070ac7b03a9") << OP_CHECKSIG;
     *
     * ie LTC = 040184710fa689ad5023690c80f3a49c8f13f8d45b8c857fbcbc8bc4a8e4d3eb4b10f4d4604fa08dce601aaf0f470216fe1b51850b4acf21b179c45070ac7b03a9
     *
     * this may be the same value as scripHex
     */
    private String pubKey;

    /**
     * DNS Seeds
     *
     * extracted from /src/chainparams.cpp class
     * vSeeds
     */
    private String[] dnsSeeds;

    /**
     * BIP32 Header P2PKH Pub
     *
     * extracted from /src/chainparams.cpp class
     * Concatenate the 4 values in base58Prefixes[EXT_PUBLIC_KEY]
     * base58Prefixes[EXT_PUBLIC_KEY] = {0x04, 0x88, 0xB2, 0x1E} = 0x0488B21E
     */
    private int bip32HeaderP2PKHpub;

    /**
     * BIP32 Header P2PKH Priv
     *
     * extracted from /src/chainparams.cpp class
     * Concatenate the 4 values in base58Prefixes[EXT_SECRET_KEY]
     * base58Prefixes[EXT_SECRET_KEY] = {0x04, 0x88, 0xAD, 0xE4} = 0x0488ADE4
     */
    private int bip32HeaderP2PKHpriv;

    /**
     * Address Header (Testnet)
     *
     * extracted from /src/chainparams.cpp class
     * base58Prefixes[PUBKEY_ADDRESS] from Testnet
     */
    private int addressHeaderTestnet;

    /**
     * BIP32 Header P2PKH Pub (Testnet)
     *
     * extracted from /src/chainparams.cpp class
     * Concatenate the 4 values in base58Prefixes[EXT_PUBLIC_KEY]
     * base58Prefixes[EXT_PUBLIC_KEY] = {0x04, 0x88, 0xB2, 0x1E} = 0x0488B21E
     */
    private int bip32HeaderP2PKHpubTestnet;

    /**
     * BIP32 Header P2PKH Priv (Testnet)
     *
     * extracted from /src/chainparams.cpp class
     * Concatenate the 4 values in base58Prefixes[EXT_SECRET_KEY]
     * base58Prefixes[EXT_SECRET_KEY] = {0x04, 0x88, 0xAD, 0xE4} = 0x0488ADE4
     */
    private int bip32HeaderP2PKHprivTestnet;

    /**
     * Id
     *
     * "org.litecoin.production" for LTC
     * I'm guessing this just has to match others for trading purposes.
     */
    private String id;

    /**
     * Majority Enforce Block Upgrade
     *
     * All coins are setting this to 750, except DOGE is setting this to 1500.
     */
    private int majorityEnforceBlockUpgrade;

    /**
     * Majority Reject Block Outdated
     *
     * All coins are setting this to 950, except DOGE is setting this to 1900.
     */
    private int majorityRejectBlockOutdated;

    /**
     * Majority Window
     *
     * All coins are setting this to 1000, except DOGE is setting this to 2000.
     */
    private int majorityWindow;

    /**
     * Code
     *
     * "LITE" for LTC
     * Currency code for full unit.
     */
    private String code;

    /**
     * mCode
     *
     * "mLITE" for LTC
     * Currency code for milli unit.
     */
    private String mCode;

    /**
     * Base Code
     *
     * "Liteoshi" for LTC
     * Currency code for base unit.
     */
    private String baseCode;

    /**
     * Min Non Dust Output
     *
     * 100000 for LTC, web search for minimum transaction fee per kB
     */
    private int minNonDustOutput;

    /**
     * URI Scheme
     *
     * uriScheme = "litecoin" for LTC
     * Do a web search to find this value.
     */
    private String uriScheme;

    /**
     * Protocol Version Minimum
     *
     * 70002 for LTC
     * extracted from /src/protocol.h class
     */
    private int protocolVersionMinimum;

    /**
     * Protocol Version Current
     *
     * 70003 for LTC
     * extracted from /src/protocol.h class
     */
    private int protocolVersionCurrent;

    /**
     * Has Max Money
     *
     * false for DOGE, true for BTC and LTC
     */
    private boolean hasMaxMoney;

    /**
     * Max Money
     *
     * 84000000 for LTC, 21000000 for BTC
     * extracted from src/amount.h class
     */
    private long maxMoney;

    /**
     * Currency Code
     *
     * The trading symbol, ie LTC, BTC, DOGE
     */
    private String currencyCode;

    /**
     * Minimum Order Amount
     *
     * web search, LTC minimumOrderAmount = 1000000, 0.01 LTC minimum order to avoid dust errors
     */
    private long minimumOrderAmount;

    /**
     * Fee Per Kb
     *
     * web search, LTC feePerKb = 10000, 0.0001 LTC per 1000 bytes
     */
    private long feePerKb;

    /**
     * Network Name
     *
     * ie Litecoin-MAIN
     */
    private String networkName;

    /**
     * Fee Required
     *
     *  web search, LTC fee required = 1000L
     */
    private long feeRequired;

    /**
     * Extended Public Key
     *
     * xpub for operations that require wallet watching
     */
    private String extendedPublicKey;

    /**
     * Send Amount
     *
     * The amount to send in base units. Also, requires sending fee per byte, receiving address and sender's extended private key.
     */
    private long sendAmount;

    /**
     * Sending Fee Per Byte
     *
     * The fee to include on a send request in base units. Also, requires receiving address, sender's extended private key and send amount.
     */
    private long sendingFeePerByte;

    /**
     * Receiving Address
     *
     * The receiving address for a send request. Also, requires send amount, sender's extended private key and sending fee per byte.
     */
    private String receivingAddress;

    /**
     * Extended Private Key
     *
     * xpriv address for a send request. Also, requires receiving address, send amount and sending fee per byte.
     */
    private String extendedPrivateKey;

    /**
     * Server Info
     *
     * For adding, removing, setting current server requests.
     */
    private ServerInfo serverInfo;

    /**
     * Script Sig
     *
     * extracted from /src/chainparams.cpp class
     * pszTimestamp
     *
     * transform this value - https://bitcoin.stackexchange.com/questions/13122/scriptsig-coinbase-structure-of-the-genesis-block
     * ie LTC = 04ffff001d0104404e592054696d65732030352f4f63742f32303131205374657665204a6f62732c204170706c65e280997320566973696f6e6172792c2044696573206174203536
     * ie DOGE = 04ffff001d0104084e696e746f6e646f
     */
    private String scriptSig;

    /**
     * Script Hex
     *
     * extracted from /src/chainparams.cpp class
     * genesisOutputScript
     *
     * ie LTC = 040184710fa689ad5023690c80f3a49c8f13f8d45b8c857fbcbc8bc4a8e4d3eb4b10f4d4604fa08dce601aaf0f470216fe1b51850b4acf21b179c45070ac7b03a9
     *
     * this may be the same value as pubKey
     */
    private String scriptHex;

    /**
     * Reward
     *
     * extracted from /src/chainparams.cpp class
     * CreateGenesisBlock(..., [reward] * COIN)
     *
     * ie LTC = 50, BTC = 50, DOGE = 88
     */
    private int reward;

    /**
     * Genesis Creation Version
     */
    private int genesisCreationVersion;

    /**
     * Genesis Block Version
     */
    private long genesisBlockVersion;

    /**
     * Genesis Time
     *
     * extracted from /src/chainparams.cpp class
     * CreateGenesisBlock(nTime, ...)
     *
     * ie LTC = 1317972665
     */
    private long genesisTime;

    /**
     * Difficulty Target
     *
     * extracted from /src/chainparams.cpp class
     * CreateGenesisBlock(genesisTime, nonce, difficultyTarget, 1, reward * COIN);
     *
     * convert from hex to decimal
     *
     * ie LTC = 0x1e0ffff0 = 504365040
     */
    private long difficultyTarget;

    /**
     * Merkle Hex
     */
    private String merkleHex;

    /**
     * Nonce
     *
     * extracted from /src/chainparams.cpp class
     * CreateGenesisBlock(genesisTime, nonce, difficultyTarget, 1, reward * COIN);
     *
     * ie LTC = 2084524493
     */
    private long nonce;


    public int getTargetTimespan() {
        return targetTimespan;
    }

    public int getTargetSpacing() {
        return targetSpacing;
    }

    public long getPacketMagic() {
        return packetMagic;
    }

    public int getPort() {
        return port;
    }

    public int getAddressHeader() {
        return addressHeader;
    }

    public int getP2shHeader() {
        return p2shHeader;
    }

    public String getSegwitAddressHrp() {
        return segwitAddressHrp;
    }

    public int getDumpedPrivateKeyHeader() {
        return dumpedPrivateKeyHeader;
    }

    public int getSubsidyDecreaseBlockCount() {
        return subsidyDecreaseBlockCount;
    }

    public String getExpectedGenesisHash() {
        return expectedGenesisHash;
    }

    public String getPubKey() {
        return pubKey;
    }

    public String[] getDnsSeeds() {
        return dnsSeeds;
    }

    public int getBip32HeaderP2PKHpub() {
        return bip32HeaderP2PKHpub;
    }

    public int getBip32HeaderP2PKHpriv() {
        return bip32HeaderP2PKHpriv;
    }

    public int getAddressHeaderTestnet() {
        return addressHeaderTestnet;
    }

    public int getBip32HeaderP2PKHpubTestnet() {
        return bip32HeaderP2PKHpubTestnet;
    }

    public int getBip32HeaderP2PKHprivTestnet() {
        return bip32HeaderP2PKHprivTestnet;
    }

    public String getId() {
        return  this.id;
    }

    public int getMajorityEnforceBlockUpgrade() {
        return this.majorityEnforceBlockUpgrade;
    }

    public int getMajorityRejectBlockOutdated() {
        return this.majorityRejectBlockOutdated;
    }

    public int getMajorityWindow() {
        return this.majorityWindow;
    }

    public String getCode() {
        return this.code;
    }

    public String getmCode() {
        return this.mCode;
    }

    public String getBaseCode() {
        return this.baseCode;
    }

    public int getMinNonDustOutput() {
        return this.minNonDustOutput;
    }

    public String getUriScheme() {
        return this.uriScheme;
    }

    public int getProtocolVersionMinimum() {
        return this.protocolVersionMinimum;
    }

    public int getProtocolVersionCurrent() {
        return this.protocolVersionCurrent;
    }

    public boolean isHasMaxMoney() {
        return this.hasMaxMoney;
    }

    public long getMaxMoney() {
        return this.maxMoney;
    }

    public String getCurrencyCode() {
        return this.currencyCode;
    }

    public long getMinimumOrderAmount() {
        return this.minimumOrderAmount;
    }

    public long getFeePerKb() {
        return this.feePerKb;
    }

    public String getNetworkName() {
        return this.networkName;
    }

    public long getFeeRequired() {
        return this.feeRequired;
    }

    public String getExtendedPublicKey() {
        return this.extendedPublicKey;
    }

    public long getSendAmount() {
        return this.sendAmount;
    }

    public long getSendingFeePerByte() {
        return this.sendingFeePerByte;
    }

    public String getReceivingAddress() {
        return this.receivingAddress;
    }

    public String getExtendedPrivateKey() {
        return this.extendedPrivateKey;
    }

    public ServerInfo getServerInfo() {
        return this.serverInfo;
    }

    public String getScriptSig() {
        return this.scriptSig;
    }

    public String getScriptHex() {
        return this.scriptHex;
    }

    public int getReward() {
        return this.reward;
    }

    public int getGenesisCreationVersion() {
        return this.genesisCreationVersion;
    }

    public long getGenesisBlockVersion() {
        return this.genesisBlockVersion;
    }

    public long getGenesisTime() {
        return this.genesisTime;
    }

    public long getDifficultyTarget() {
        return this.difficultyTarget;
    }

    public String getMerkleHex() {
        return this.merkleHex;
    }

    public long getNonce() {
        return this.nonce;
    }

    @Override
    public String toString() {
        return "BitcoinyTBDRequest{" +
                "targetTimespan=" + targetTimespan +
                ", targetSpacing=" + targetSpacing +
                ", packetMagic=" + packetMagic +
                ", port=" + port +
                ", addressHeader=" + addressHeader +
                ", p2shHeader=" + p2shHeader +
                ", segwitAddressHrp='" + segwitAddressHrp + '\'' +
                ", dumpedPrivateKeyHeader=" + dumpedPrivateKeyHeader +
                ", subsidyDecreaseBlockCount=" + subsidyDecreaseBlockCount +
                ", expectedGenesisHash='" + expectedGenesisHash + '\'' +
                ", pubKey='" + pubKey + '\'' +
                ", dnsSeeds=" + Arrays.toString(dnsSeeds) +
                ", bip32HeaderP2PKHpub=" + bip32HeaderP2PKHpub +
                ", bip32HeaderP2PKHpriv=" + bip32HeaderP2PKHpriv +
                ", addressHeaderTestnet=" + addressHeaderTestnet +
                ", bip32HeaderP2PKHpubTestnet=" + bip32HeaderP2PKHpubTestnet +
                ", bip32HeaderP2PKHprivTestnet=" + bip32HeaderP2PKHprivTestnet +
                ", id='" + id + '\'' +
                ", majorityEnforceBlockUpgrade=" + majorityEnforceBlockUpgrade +
                ", majorityRejectBlockOutdated=" + majorityRejectBlockOutdated +
                ", majorityWindow=" + majorityWindow +
                ", code='" + code + '\'' +
                ", mCode='" + mCode + '\'' +
                ", baseCode='" + baseCode + '\'' +
                ", minNonDustOutput=" + minNonDustOutput +
                ", uriScheme='" + uriScheme + '\'' +
                ", protocolVersionMinimum=" + protocolVersionMinimum +
                ", protocolVersionCurrent=" + protocolVersionCurrent +
                ", hasMaxMoney=" + hasMaxMoney +
                ", maxMoney=" + maxMoney +
                ", currencyCode='" + currencyCode + '\'' +
                ", minimumOrderAmount=" + minimumOrderAmount +
                ", feePerKb=" + feePerKb +
                ", networkName='" + networkName + '\'' +
                ", feeRequired=" + feeRequired +
                ", extendedPublicKey='" + extendedPublicKey + '\'' +
                ", sendAmount=" + sendAmount +
                ", sendingFeePerByte=" + sendingFeePerByte +
                ", receivingAddress='" + receivingAddress + '\'' +
                ", extendedPrivateKey='" + extendedPrivateKey + '\'' +
                ", serverInfo=" + serverInfo +
                ", scriptSig='" + scriptSig + '\'' +
                ", scriptHex='" + scriptHex + '\'' +
                ", reward=" + reward +
                ", genesisCreationVersion=" + genesisCreationVersion +
                ", genesisBlockVersion=" + genesisBlockVersion +
                ", genesisTime=" + genesisTime +
                ", difficultyTarget=" + difficultyTarget +
                ", merkleHex='" + merkleHex + '\'' +
                ", nonce=" + nonce +
                '}';
    }
}
