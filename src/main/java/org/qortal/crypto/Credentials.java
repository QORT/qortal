package org.qortal.crypto;

import java.io.*;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;

public class Credentials {

	public static Certificate readCertFile(String certPath) throws CertificateException {
		if (certPath == null || !new File(certPath).exists())
			return null;

		CertificateFactory certFactory;
		try {
			certFactory = CertificateFactory.getInstance("X509", "BC");
			File certFile = new File(certPath);
			try (InputStream certStream = new FileInputStream(certFile)) {
				return certFactory.generateCertificate(certStream);
			}
		} catch (FileNotFoundException e) {
			return null;
		} catch (CertificateException | IOException | NoSuchProviderException e) {
			throw new CertificateException(e);
		}
	}

	public static Certificate readCertResource(String certResource) throws CertificateException {
		ClassLoader loader = Credentials.class.getClassLoader();
		try (InputStream inputStream = loader.getResourceAsStream(certResource)) {
			if (inputStream == null)
				return null;

			CertificateFactory certFactory;
			try {
				certFactory = CertificateFactory.getInstance("X509", "BC");
				return certFactory.generateCertificate(inputStream);
			} catch (CertificateException | NoSuchProviderException e) {
				throw new CertificateException(e);
			}
		} catch (IOException e) {
			return null;
		}
	}

	public static PrivateKey loadPrivateKey(String privateKeyPath, String keyAlgorithm) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		if (privateKeyPath == null || !new File(privateKeyPath).exists())
			throw new FileNotFoundException("Private key file not found at " + privateKeyPath);

		File file = new File(privateKeyPath);
		byte[] privKeyBytes = new byte[(int) file.length()];
		try (InputStream in = new FileInputStream(file)) {
			in.read(privKeyBytes);
		}
		KeyFactory keyFactory = KeyFactory.getInstance(keyAlgorithm);
		KeySpec ks = new PKCS8EncodedKeySpec(privKeyBytes);
		return keyFactory.generatePrivate(ks);
	}

}
