package com.soffid.iam.sync.engine.cert;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import javax.xml.rpc.ServiceException;

import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.User;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownHostException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.signatura.cliente.ValidadorCertificados;
import es.caib.signatura.cliente.XML;
import es.caib.signatura.utils.BitException;
import es.caib.signatura.validacion.ResultadoValidacion;

public class CertificateManager {

    private ValidadorCertificados validador;
    Logger log = Log.getLogger("CertificateManager");

    public CertificateManager() throws RemoteException, InternalErrorException {
        validador = new ValidadorFactory().getValidador();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public boolean verifyCertificate(X509Certificate[] certificateChain)
            throws CertificateEncodingException, BitException, RemoteException,
            ServiceException {
        boolean ok = true;
        for (int i = 0; i < certificateChain.length; i++) {
            ByteArrayInputStream fileIS = new ByteArrayInputStream(
                    certificateChain[i].getEncoded());
            byte resultado[] = validador
                    .validarCertificadoAutenticacion(fileIS);
            if (XML.verificaFirma(resultado)) {
                ArrayList lista = XML.getResultadosValidacion(resultado);
                ResultadoValidacion[] resultados = new ResultadoValidacion[lista
                        .size()];
                lista.toArray(resultados);

                for (int j = 0; j < resultados.length; j++) {
                    if (resultados[i].getValido().booleanValue()) {
                        log.info("Certificate accepted", null, null);
                        continue;
                    } else {
                        log.warn("Certificate not valid {}",
                                certificateChain[i].getSubjectDN().toString(),
                                null);
                        ok = false;
                    }
                    // Obtenemos las causas del error
                    ArrayList lista_causas = resultados[i]
                            .getListaCausasNoValidado();
                    for (int k = 0; k < lista_causas.size(); k++) {
                        BitException result = (BitException) lista_causas
                                .get(k);
                        log.warn("Error {}: {}", result.getCode(),
                                result.getTextoAdicional());
                    }

                }
            }
        }
        return ok;
    }

    public User getCertificatUser(X509Certificate userCertificate)
            throws UnknownUserException, InternalErrorException,
            CertificateEncodingException, IOException, UnknownHostException {

        ServerService s = ServerServiceLocator.instance().getServerService();
        
        return s.getUserInfo(new X509Certificate[] { userCertificate} );
    }
}
