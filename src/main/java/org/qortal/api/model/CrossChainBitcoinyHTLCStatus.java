package org.qortal.api.model;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlAccessorType(XmlAccessType.FIELD)
public class CrossChainBitcoinyHTLCStatus {

	@Schema(description = "P2SH address", example = "3CdH27kTpV8dcFHVRYjQ8EEV5FJg9X8pSJ (mainnet), 2fMiRRXVsxhZeyfum9ifybZvaMHbQTmwdZw (testnet)")
	public String bitcoinP2shAddress;

	@Schema(description = "P2SH balance")
	public BigDecimal bitcoinP2shBalance;

	@Schema(description = "Can HTLC redeem yet?")
	public boolean canRedeem;

	@Schema(description = "Can HTLC refund yet?")
	public boolean canRefund;

	@Schema(description = "Secret used by HTLC redeemer")
	public byte[] secret;

	public CrossChainBitcoinyHTLCStatus() {
	}

}
