package com.soffid.iam.sync.bootstrap;

import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Enumeration;

import org.apache.commons.logging.LogFactory;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.api.Password;
import com.soffid.iam.api.Server;
import com.soffid.iam.api.Tenant;
import com.soffid.iam.config.Config;
import com.soffid.iam.model.identity.IdentityGeneratorBean;
import com.soffid.iam.remote.RemoteServiceLocator;
import com.soffid.iam.service.ApplicationBootService;
import com.soffid.iam.service.DispatcherService;
import com.soffid.iam.service.TenantService;
import com.soffid.iam.ssl.ConnectionFactory;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.cert.CertificateServer;
import com.soffid.iam.sync.engine.log.LogConfigurator;
import com.soffid.iam.sync.service.ServerService;
import com.soffid.iam.utils.Security;
import com.soffid.tools.db.persistence.XmlReader;
import com.soffid.tools.db.schema.Column;
import com.soffid.tools.db.schema.Database;
import com.soffid.tools.db.schema.Table;
import com.soffid.tools.db.updater.DBUpdater;
import com.soffid.tools.db.updater.MsSqlServerUpdater;
import com.soffid.tools.db.updater.MySqlUpdater;
import com.soffid.tools.db.updater.OracleUpdater;
import com.soffid.tools.db.updater.PostgresqlUpdater;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.TargetError;
import es.caib.seycon.ng.comu.ServerType;
import es.caib.seycon.ng.exception.CertificateEnrollDenied;
import es.caib.seycon.ng.exception.CertificateEnrollWaitingForAproval;
import es.caib.seycon.ng.exception.InternalErrorException;
import es.caib.seycon.ng.exception.UnknownUserException;

public class Script {
    static org.apache.commons.logging.Log log  = LogFactory.getLog(Script.class);

    public static void main(String args[]) throws Exception {
        LogConfigurator.configureMinimalLogging();
        Security.onSyncServer();
        ServiceLocator serviceLocator = ServerServiceLocator.instance();

        if (args.length == 0) {
            System.out.println("Missing script name");
            System.exit(1);
        }

        if ( args.length > 0 ) {
			String filename = args[0];

			String [] bshArgs;
			if ( args.length > 1 ) {
				bshArgs = new String [ args.length -1 ];
				System.arraycopy( args, 1, bshArgs, 0, args.length-1 );
			} else
				bshArgs = new String [0];

            Interpreter interpreter = new Interpreter();
			interpreter.set( "bsh.args", bshArgs );
			interpreter.set( "serviceLocator", serviceLocator );
			try {
				Object result = 
					interpreter.source( filename, interpreter.getNameSpace() );
				if ( result instanceof Class )
					try {
						Interpreter.invokeMain( (Class)result, bshArgs );
					} catch ( Exception e ) 
					{
						Object o = e;
						if ( e instanceof InvocationTargetException )
							o = ((InvocationTargetException)e)
								.getTargetException();
						System.err.println(
							"Class: "+result+" main method threw exception:"+o);
					}
			} catch ( FileNotFoundException e ) {
				System.out.println("File not found: "+e);
			} catch ( TargetError e ) {
				System.out.println("Script threw exception: "+e);
				if ( e.inNativeCode() )
					e.printStackTrace( System.err );
			} catch ( EvalError e ) {
				System.out.println("Evaluation Error: "+e);
			} catch ( IOException e ) {
				System.out.println("I/O Error: "+e);
			}
        } else 
		{
			InputStream src = System.in;

            Reader in = new CommandLineReader( new InputStreamReader(src));
            Interpreter interpreter = 
				new Interpreter( in, System.out, System.err, true );
        	interpreter.run();
        }
    }
}
