package org.qortal.api.model.crosschain;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class AddressRequest {

	@Schema(description = "Litecoin BIP32 extended public key", example = "tpub___________________________________________________________________________________________________________")
	public String xpub58;

	public AddressRequest() {
	}

}
