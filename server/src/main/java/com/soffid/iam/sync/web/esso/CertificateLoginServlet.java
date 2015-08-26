package com.soffid.iam.sync.web.esso;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.rmi.RemoteException;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.rpc.ServiceException;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.FileUploadBase.SizeLimitExceededException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.bouncycastle.cms.CMSSignedDataParser;
import org.bouncycastle.cms.CMSTypedStream;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.jce.PKCS7SignedData;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.provider.X509CertParser;
import org.bouncycastle.x509.util.StreamParsingException;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import com.soffid.iam.api.Challenge;
import com.soffid.iam.api.User;
import com.soffid.iam.api.sso.Secret;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.SoffidApplication;
import com.soffid.iam.sync.engine.cert.CertificateManager;
import com.soffid.iam.sync.engine.cert.ValidadorFactory;
import com.soffid.iam.sync.engine.challenge.ChallengeStore;
import com.soffid.iam.sync.service.LogonService;
import com.soffid.iam.sync.service.ServerService;
import com.soffid.iam.sync.web.NameMismatchException;
import com.soffid.iam.util.NameParser;

import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;
import es.caib.seycon.ng.servei.CertificateParser;
import es.caib.seycon.util.Base64;
import es.caib.signatura.api.SignatureVerifyException;
import es.caib.signatura.cliente.ValidadorCertificados;
import es.caib.signatura.cliente.XML;
import es.caib.signatura.utils.BitException;
import es.caib.signatura.validacion.ResultadoValidacion;

public class CertificateLoginServlet extends HttpServlet {

    private ValidadorCertificados validador;

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            validador = new ValidadorFactory().getValidador();
        } catch (Exception e) {
            e.printStackTrace();
            throw new ServletException(e);
        }
    }

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    Logger log = Log.getLogger("CertificateLoginServlet");
    private X509Certificate userCertificate;
    private X509Certificate[] certificateChain;

    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String action = req.getParameter("action");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(resp
                .getOutputStream(), "UTF-8"));
        try {
            if (action == null)
                writer.write(doStartTestAction(req, resp));
            else if ("test".equals(action))
                writer.write(doTestAction(req, resp));
            else if ("start".equals(action))
                writer.write(doStartAction(req, resp));
            else if ("continue".equals(action))
                writer.write(doContinueAction(req, resp));
            else
                throw new Exception("Invalid action " + action);
        } catch (Exception e) {
            log.warn("Error performing certificate login", e);
            writer.write(e.getClass().getName() + "|" + e.getMessage() + "\n");
        }
        writer.close();

    }

    private static ChallengeStore challengeStore = ChallengeStore.getInstance();

    private String doStartTestAction(HttpServletRequest req,
            HttpServletResponse resp) throws Exception {
        StringBuffer buf = new StringBuffer();
        resp.setContentType("text/html; charset='UTF-8'");
        buf.append("<HTML><FORM method='POST' enctype='multipart/form-data' action='/certificateLogin'>");
        buf.append("Sel·leccioni el certificat<br>");
        buf.append("<input type=hidden name='action' value='test'><br>");
        buf.append("<input type=file name=cert><br>");
        buf.append("<input type=submit value=Acceptar></FORM></HTML>");
        return buf.toString();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(resp
                .getOutputStream(), "UTF-8"));
        try {
            writer.write(doTestAction(req, resp));
        } catch (Exception e) {
            log.warn("Error performing certificate login", e);
            writer.write(e.getClass().getName() + "<br>" + e.getMessage().replaceAll("\n", "<br>") + "<br>");
        }
        writer.close();
    }

    private String doTestAction(HttpServletRequest req,
            HttpServletResponse resp) throws Exception {
        DiskFileItemFactory diskFileItemFactory = new DiskFileItemFactory();
        diskFileItemFactory.setSizeThreshold(100000);


        ServletFileUpload servletFileUpload = new ServletFileUpload(
                diskFileItemFactory);
        servletFileUpload.setSizeMax(10000000);
        certificateChain = null;
        userCertificate = null;
        try {
                List<FileItem> fileItemsList = servletFileUpload
                                .parseRequest(req);
                Iterator<FileItem> it = fileItemsList.iterator();
                while (it.hasNext()) {
                        FileItem fileItem = it.next();
                        if (!fileItem.isFormField()) {
                           InputStream inputStream = fileItem.getInputStream();
                           ByteArrayOutputStream out = new ByteArrayOutputStream();
                           byte data[] = new byte[1024];
                           int readedBytes = 0;
                           while ((readedBytes = inputStream.read(data)) > -1) {
                                out.write(data, 0, readedBytes);
                           }
                           inputStream.close();
                           X509CertParser parser = new X509CertParser();
                           parser.engineInit(new ByteArrayInputStream(out.toByteArray()));
                           X509Certificate x509Cert = (X509Certificate) parser.engineRead();
                           Vector<X509Certificate> v = new Vector<X509Certificate>();
                           while (x509Cert != null)
                           {
                               v.add(x509Cert);
                               x509Cert = (X509Certificate) parser.engineRead();
                           }
                           certificateChain = (X509Certificate[]) v.toArray(new X509Certificate[v.size()]);
                           userCertificate = certificateChain[0];
                           
                        }
                }
        } catch (SizeLimitExceededException ex) {
            throw ex;
        } catch (FileUploadException th) {
                throw th;
        } catch (IOException th) {
                throw th;
        }
        if (userCertificate == null)
            throw new ServletException("No certificate uploaded");
        StringBuffer buf = new StringBuffer();
        resp.setContentType("text/html; charset='UTF-8'");
        buf.append("<HTML>");
        buf.append("Certificate:");
        buf.append (userCertificate.getSubjectDN().toString());
        buf.append("<BR>");
        CertificateManager mgr = new CertificateManager();
        if (mgr.verifyCertificate(certificateChain))
            buf.append ("VALID<br>");
        else
            buf.append ("NOT VALID<br>");

        User user = validateUser();
        if (user != null) {
            buf.append ("Associated user: ");
            buf.append(user.getUserName());
            buf.append("<br>");
        }
        return buf.toString();
    }
    
    private String doStartAction(HttpServletRequest req,
            HttpServletResponse resp) throws Exception {
        String hostIP = req.getRemoteAddr();
        
        ServerService ss = ServerServiceLocator.instance().getServerService();
        LogonService ls = ServerServiceLocator.instance().getLogonService();
        
        User usuari = ss.getUserInfo(new X509Certificate[] {userCertificate});

        final Challenge challenge = ls.requestChallenge(Challenge.TYPE_CERT,usuari.getUserName(), null, hostIP, null, Challenge.CARD_IFNEEDED);

        return "OK|" + challenge.getChallengeId();
    }

    private String doContinueAction(HttpServletRequest req,
            HttpServletResponse resp) throws Exception {
        final Challenge challenge = getChallenge(req);
        challengeStore.removeChallenge(challenge);
        final String pkcs7 = req.getParameter("pkcs7");
        if (pkcs7 == null) {
            final String pkcs1 = req.getParameter("signature");
            final String cert = req.getParameter("cert");
            return pkcs1Login(challenge, pkcs1, cert);
        } else {
            return pkcs7Login(challenge, pkcs7);
        }

    }

    private String pkcs1Login(Challenge challenge, String pkcs1, String cert) throws StreamParsingException, InternalErrorException, CertificateEncodingException, ServiceException, UnknownUserException, IOException {
        byte certBytes[] = Base64.decode(cert);
        X509CertParser parser = new X509CertParser();
        Vector v = new Vector();
        parser.engineInit(new ByteArrayInputStream(certBytes));
        X509Certificate x509Cert = (X509Certificate) parser.engineRead();
        while (x509Cert != null)
        {
            v.add(x509Cert);
            x509Cert = (X509Certificate) parser.engineRead();
        }
        certificateChain = (X509Certificate[]) v.toArray(new X509Certificate[v.size()]);
        userCertificate = certificateChain[0];
        if (!verifySignaturePKCS1(challenge.getChallengeId(), pkcs1))
            return "ERROR|Signatura incorrecta";
        CertificateManager mgr = new CertificateManager();
        if (!mgr.verifyCertificate(certificateChain))
            return "ERROR|Certificat incorrecte";
        return getCredentials();
    }

    private String pkcs7Login(final Challenge challenge, final String pkcs7)
            throws InternalErrorException, CertificateEncodingException,
            BitException, RemoteException, ServiceException,
            UnknownUserException, IOException {
        if (!verifySignaturePKCS7(challenge.getChallengeId(), pkcs7))
            return "ERROR|Signatura incorrecta";
        CertificateManager mgr = new CertificateManager();
        if (!mgr.verifyCertificate(certificateChain))
            return "ERROR|Certificat incorrecte";
        return getCredentials();
    }

    private String getCredentials() throws UnknownUserException,
            InternalErrorException, CertificateEncodingException, IOException {
        try {
            User user = validateUser();
            StringBuffer result = new StringBuffer("OK");
            
            List<Secret> secrets = ServerServiceLocator.instance().getSecretStoreService().getSecrets(user);
            for (Iterator<Secret> it = secrets.iterator(); it.hasNext();) {
                Secret s = it.next();
                result.append('|');
                result.append(s.getName());
                result.append('|');
                result.append(s.getValue()
                        .getPassword());
            }
            return result.toString();

        } catch (NameMismatchException e) {
            return "ERROR|El nom del certificat no coincideix: "
                    + e.getMessage();
        }
    }

    User validateUser() throws UnknownUserException, InternalErrorException,
            CertificateEncodingException, IOException, NameMismatchException {
        ServerService s = ServerServiceLocator.instance().getServerService();
        User ui = s.getUserInfo(new X509Certificate[] {userCertificate});
        CertificateParser parser = new CertificateParser(userCertificate);
        String certNom = parser.getGivenName();
        String certLlinatge1 = parser.getFirstSurName();
        String certLlinatge2 = parser.getSecondSurName();
        // Reparse de nom i llinatges
        // Primer Intent
        
        NameParser p = new NameParser();
        String dbNom = p.normalizeName(ui.getFirstName());
        String dbLlinatge1 = p.normalizeName(ui.getLastName());
        String dbLlinatge2 = ui.getMiddleName() == null ? "" : p.normalizeName(ui.getMiddleName());

        if (!dbNom.equals(certNom) 
                || !dbLlinatge1.equals(certLlinatge1)
                || !dbLlinatge2.equals(certLlinatge2)) {
            // Segon intent
            String dbLlinatges [] = p.parse(ui.getLastName()+" "+ui.getMiddleName(), 2);
            if (!dbNom.equals(certNom) 
                    || !dbLlinatges[0].equals(certLlinatge1)
                    || !dbLlinatges[1].equals(certLlinatge2)) {
                throw new NameMismatchException(certNom + " "
                        + certLlinatge1 + " " + certLlinatge2 + "/" + dbNom
                        + " " + dbLlinatge1 + " " + dbLlinatge2);
            } 
        }
        return ui;
    }

    @SuppressWarnings("deprecation")
    boolean verifySignaturePKCS7(String key, String pkcs7string)
            throws InternalErrorException {
        boolean verified = true;
        try {
            userCertificate = null;
            certificateChain = null;

            ByteArrayInputStream content = new ByteArrayInputStream(key
                    .getBytes("ISO-8859-1"));
            byte[] pkcs7 = Base64.decode(pkcs7string);
            // Verificación de la firma del documento
            CMSTypedStream typedIn = new CMSTypedStream(content);

            CMSSignedDataParser parser = new CMSSignedDataParser(typedIn, pkcs7);
            CMSTypedStream in = parser.getSignedContent();
            in.drain();

            // Obenir els certificats del PKCS7
            CertStore certs = parser.getCertificatesAndCRLs("Collection", "BC");

            // Obtenir les dades del primer (i únic) signant
            SignerInformationStore signersStore = parser.getSignerInfos();
            Collection signers = signersStore.getSigners();
            Iterator it = signers.iterator();
            byte[] digest;
            if (it.hasNext()) {
                SignerInformation signer = (SignerInformation) it.next();
                // Obtenir el certificat del signatari
                Collection certCollection = certs.getCertificates(signer
                        .getSID());
                Iterator certIt = certCollection.iterator();
                if (certIt.hasNext()) {
                    userCertificate = (X509Certificate) certIt.next();
                }
                /*
                 * Se recuperan todos los certificados
                 */
                certCollection = certs.getCertificates(null);
                certIt = certCollection.iterator();
                LinkedList allCertificates = new LinkedList();
                while (certIt.hasNext()) {
                    allCertificates.addFirst(certIt.next());
                }
                // Se construeix la cadena de certificació.
                X509Certificate currentCertificate = userCertificate;
                LinkedList certificateChainList = new LinkedList();
                certificateChainList.addFirst(userCertificate);
                boolean finishExtraction = false;
                while (!finishExtraction) {
                    ListIterator iterator = allCertificates.listIterator();
                    boolean nextCertificate = false;
                    X509Certificate certificateFromIterator = null;
                    while (iterator.hasNext() && !nextCertificate) {
                        certificateFromIterator = (X509Certificate) iterator
                                .next();
                        nextCertificate = certificateFromIterator
                                .getSubjectDN().toString().compareTo(
                                        currentCertificate.getIssuerDN()
                                                .toString()) == 0;
                    }
                    if (nextCertificate) {
                        certificateChainList.addLast(certificateFromIterator);
                        currentCertificate = certificateFromIterator;
                    }
                    finishExtraction = !nextCertificate
                            || currentCertificate.getIssuerDN().toString()
                                    .compareTo(
                                            currentCertificate.getSubjectDN()
                                                    .toString()) == 0;
                }
                certificateChain = (X509Certificate[]) certificateChainList
                        .toArray(new X509Certificate[certificateChainList
                                .size()]);

                verified = verified && signer.verify(userCertificate, "BC");
            } else {
                throw new SignatureVerifyException(new Exception("No signer"));
            }
        } catch (Exception e) {
            log.debug("Error validating signature", e);
            throw new InternalErrorException(e.toString());
        }
        return verified;

    }

    boolean verifySignaturePKCS1(String key, String pkcs7string)
            throws InternalErrorException {
        boolean verified = true;
        try {
            byte[] pkcs1 = Base64.decode(pkcs7string);
            boolean signed = false;
            Signature s  = Signature.getInstance ("NONEwithRSA");
            if (s == null)
                throw new InternalErrorException("Invalid algorith NONEwithRSA");
            s.initVerify ( userCertificate.getPublicKey() );
            s.update ( key.getBytes("ISO-8859-1") );
            if ( s.verify (pkcs1) ) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            log.debug("Error validating signature", e);
            throw new InternalErrorException(e.toString());
        }
    }

    private Challenge getChallenge(HttpServletRequest req)
            throws InternalErrorException {
        String challengeId = req.getParameter("challengeId");
        final Challenge challenge = challengeStore.getChallenge(challengeId);

        if (challenge == null)
            throw new InternalErrorException("Invalid token " + challengeId);
        if (!challenge.getHost().equals(req.getRemoteHost())) {
            log.warn("Ticket spoofing detected from {}", req.getRemoteHost(),
                    null);
            throw new InternalErrorException("Invalid token " + challengeId);
        }
        return challenge;
    }

}

