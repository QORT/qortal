package org.qortal.api;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.math.BigDecimal;

public class RewardSharePercentTypeAdapter extends XmlAdapter<String, Integer> {

	@Override
	public Integer unmarshal(String input) throws Exception {
		if (input == null)
			return null;

		return new BigDecimal(input).setScale(2).unscaledValue().intValue();
	}

	@Override
	public String marshal(Integer output) throws Exception {
		if (output == null)
			return null;

		return String.format("%d.%02d", output / 100, Math.abs(output % 100));
	}

}
