package org.qortal.api;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.math.BigDecimal;

public class BigDecimalTypeAdapter extends XmlAdapter<String, BigDecimal> {

	@Override
	public BigDecimal unmarshal(String input) throws Exception {
		if (input == null)
			return null;

		return new BigDecimal(input).setScale(8);
	}

	@Override
	public String marshal(BigDecimal output) throws Exception {
		if (output == null)
			return null;

		return output.toPlainString();
	}

}
