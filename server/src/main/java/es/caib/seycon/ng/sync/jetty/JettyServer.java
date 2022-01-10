package es.caib.seycon.ng.sync.jetty;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;

import org.eclipse.jetty.webapp.WebAppContext;

import com.soffid.iam.remote.PublisherInterface;

public class JettyServer implements PublisherInterface
{
	com.soffid.iam.sync.jetty.JettyServer delegate;
	
    public JettyServer(com.soffid.iam.sync.jetty.JettyServer delegate) {
		super();
		this.delegate = delegate;
	}

	public void publish(Object target, String path,
            String role) throws Exception {
    	delegate.publish(target, path, role);
    }
	
    public void bindAdministrationServlet(String url, String[] rol, Class servletClass) {
        delegate.bindAdministrationServlet(url, rol, servletClass);
    }

    public void bindServiceServlet(String url,  String [] rol, Class servletClass) {
    	delegate.bindDownloadServlet(url, rol, servletClass);
    }

    public synchronized void bindServlet(String url, String constraintName, String [] rol, WebAppContext context, Class servletClass) {
    	delegate.bindServlet(url, constraintName, rol, context, servletClass);
    }

	public void bind(URL httpURL, Object target, String rol) {
		delegate.bind(httpURL, target, rol);
	}

	public void bindSEU(URL httpURL, Object target, String rol) {
		delegate.bindSEU(httpURL, target, rol);
	}

	public void bind(String url, Object target, String rol) {
		delegate.bind(url, target, rol);
	}

	public void bindSEU(String url, Object target, String rol) {
		delegate.bindSEU(url, target, rol);
	}

	public void bindAdministrationWeb() throws FileNotFoundException,
			IOException {
		delegate.bindAdministrationWeb();
	}

	public void bindDiagnostics() {
		delegate.bindDiagnostics();
	}

}
