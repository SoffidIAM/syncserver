package com.soffid.iam.sync.jetty;

import java.io.IOException;
import java.net.Socket;

import javax.net.ssl.SSLException;

import org.mortbay.jetty.security.SslSocketConnector;
import org.mortbay.log.Log;

public class MySslSocketConnector extends SslSocketConnector {
    /* ------------------------------------------------------------ */
    public void accept(int acceptorID)
        throws IOException, InterruptedException
    {   
        try
        {
            Socket socket = _serverSocket.accept();
            configure(socket);

            Connection connection=new MySslConnection(socket);
            connection.dispatch();
        }
        catch(SSLException e)
        {
            Log.warn(e);
            try
            {
                stop();
            }
            catch(Exception e2)
            {
                throw new IllegalStateException(e2);
            }
        }
    }
    
    protected class MySslConnection extends SslSocketConnector.SslConnection
    {
        public MySslConnection(Socket socket) throws IOException {
            super(socket);
        }

        public String toString () {
            return "SSL Connection from "+_socket.getRemoteSocketAddress().toString()+" to "+
                    _socket.getLocalSocketAddress().toString();
        }
        
    }

}
