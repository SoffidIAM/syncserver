package com.soffid.iam.sync.bootstrap;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.sql.SQLException;

import org.json.JSONException;

import com.soffid.iam.sync.bootstrap.impl.KubernetesConfig;

public class KubernetesSaver {
	public static void main(String args[]) throws KeyManagementException, UnrecoverableKeyException, FileNotFoundException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, JSONException, InterruptedException, SQLException {
		new KubernetesConfig().save();
	}
}
