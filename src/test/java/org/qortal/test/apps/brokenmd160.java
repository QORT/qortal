package org.qortal.test.apps;

import com.google.common.hash.HashCode;
import org.qortal.crypto.BrokenMD160;

@SuppressWarnings("deprecation")
public class brokenmd160 {

	public static void main(String[] args) {
		if (args.length == 0) {
			System.err.println("usage: broken-md160 <hex>\noutputs: hex");
			System.exit(1);
		}

		byte[] raw = HashCode.fromString(args[0]).asBytes();
		BrokenMD160 brokenMD160 = new BrokenMD160();
		byte[] digest = brokenMD160.digest(raw);

		System.out.println(HashCode.fromBytes(digest).toString());
	}

}
