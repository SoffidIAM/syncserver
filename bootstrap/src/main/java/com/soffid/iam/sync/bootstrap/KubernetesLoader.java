package com.soffid.iam.sync.bootstrap;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import com.soffid.iam.sync.bootstrap.impl.KubernetesConfig;

public class KubernetesLoader {
	public static void main(String args[]) throws KeyManagementException, UnrecoverableKeyException, FileNotFoundException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		new KubernetesConfig().load();
	}
}
