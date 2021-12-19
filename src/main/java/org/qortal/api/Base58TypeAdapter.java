package org.qortal.api;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.qortal.utils.Base58;

public class Base58TypeAdapter extends XmlAdapter<String, byte[]> {

	@Override
	public byte[] unmarshal(String input) throws Exception {
		if (input == null)
			return null;

		return Base58.decode(input);
	}

	@Override
	public String marshal(byte[] output) throws Exception {
		if (output == null)
			return null;

		return Base58.encode(output);
	}

}
