package org.qortal.api.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.math.BigDecimal;
import java.math.BigInteger;

@XmlAccessorType(XmlAccessType.FIELD)
public class BlockMintingInfo {

	public byte[] minterPublicKey;
	public String minterAddress;
	public int minterLevel;
	public int onlineAccountsCount;
	public BigDecimal maxDistance;
	public BigInteger keyDistance;
	public double keyDistanceRatio;
	public long timestamp;
	public long timeDelta;

	public BlockMintingInfo() {
	}
}
