package es.caib.seycon.ng.sync.engine.cert;

import java.rmi.RemoteException;
import java.util.Properties;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.sync.ServerServiceLocator;
import es.caib.seycon.ng.sync.servei.ServerService;
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