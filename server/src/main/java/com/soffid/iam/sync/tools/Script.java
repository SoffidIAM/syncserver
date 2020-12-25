package com.soffid.iam.sync.tools;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;

import org.apache.commons.logging.LogFactory;

import com.soffid.iam.ServiceLocator;
import com.soffid.iam.sync.ServerServiceLocator;
import com.soffid.iam.sync.engine.log.LogConfigurator;
import com.soffid.iam.utils.Security;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.TargetError;

public class Script {
    static org.apache.commons.logging.Log log  = LogFactory.getLog(Script.class);

    public static void main(String args[]) throws Exception {
        LogConfigurator.configureMinimalLogging();
        Security.onSyncServer();
        ServiceLocator serviceLocator = ServerServiceLocator.instance();
		System.setProperty("org.apache.cxf.useSpringClassHelpers", "0");

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
