package com.soffid.iam.sync.engine;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class LogWriter extends Writer {
	Log log = LogFactory.getLog("out");

	StringBuffer sb = new StringBuffer();
	
	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
//		log.info(new String (cbuf, off, len));
		int start = 0;
		for (int i = 0; i < len; i++)
		{
			if ( cbuf [i+off] == '\n')
			{
				sb.append( cbuf, off + start, i - start );
				log.info( sb.toString()  );
				sb.setLength(0);
				start = i+1;
			}				
		}
		sb.append( cbuf, off + start, len - start );
	}

	@Override
	public void flush() throws IOException {
		if (sb.length() > 0)
			log.info(sb.toString());
		sb.setLength(0);
	}

	@Override
	public void close() throws IOException {
	}
	
}
