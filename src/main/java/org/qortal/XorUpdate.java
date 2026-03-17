package org.qortal;

import org.qortal.controller.AutoUpdate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class XorUpdate {

	private static final byte XOR_VALUE = AutoUpdate.XOR_VALUE;

	public static void main(String[] args) {
		if (args.length != 2) {
			System.err.println("usage: XorUpdate <input-file> <output-file>");
			System.exit(1);
		}

		Path inPath = Paths.get(args[0]);
		if (!Files.isReadable(inPath)) {
			System.err.println(String.format("Cannot open '%s'", args[0]));
			System.exit(2);
		}

		Path outPath = Paths.get(args[1]);

		try (InputStream in = Files.newInputStream(inPath); OutputStream out = Files.newOutputStream(outPath)) {
			byte[] buffer = new byte[1024 * 1024];
			do {
				int nread = in.read(buffer);
				if (nread == -1)
					break;

				for (int i = 0; i < nread; ++i)
					buffer[i] ^= XOR_VALUE;

				out.write(buffer, 0, nread);
			} while (true);
			out.flush();
		} catch (IOException e) {
			System.err.println(e.getLocalizedMessage());

			try {
				Files.deleteIfExists(outPath);
			} catch (IOException e1) {
				System.err.println(e.getLocalizedMessage());
			}

			System.exit(2);
		}

		System.exit(0);
	}

}
