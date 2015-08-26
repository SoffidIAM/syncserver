package com.soffid.iam.sync.engine.cert;

import java.rmi.RemoteException;
import java.util.Properties;

import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.service.ServerService;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.signatura.cliente.ValidadorCertificados;

public class ValidadorFactory {

    public ValidadorCertificados getValidador() throws RemoteException, InternalErrorException {
        ServerService s  = ServerServiceLocator.instance().getServerService();
        String entorno = s.getConfig("valcert.environment");
        String url = s.getConfig("valcert.url");
        Properties props = new Properties();
        props.put("ENTORNO", entorno == null ? "PRODUCCION" : entorno);
        props
                .put(
                        "URL_SERVIDOR",
                        url == null ? "https://intranet.caib.es/signatura/services/ValidacionCertificados"
                                : url);
        return new ValidadorCertificados(props);
    }

    public ValidadorFactory() {
    }
}