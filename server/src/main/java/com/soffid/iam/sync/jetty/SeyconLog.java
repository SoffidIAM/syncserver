package com.soffid.iam.sync.jetty;

import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.mortbay.log.Logger;

public class SeyconLog implements Logger {
    private static DateFormat dateFormat;
    private static boolean debug = false;
    private String name;
    private static PrintStream stream = System.err;
    
    static
    {
        try
        {
            dateFormat=new SimpleDateFormat("HH:mm:ss.SSS");
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        
    }
    
    public SeyconLog()
    {
        this(null);
    }
    
    public SeyconLog(String name)
    {    
        this.name=name==null?"":name;
    }
    
    public boolean isDebugEnabled()
    {
        return debug;
    }
    
    public void setDebugEnabled(boolean enabled)
    {
        debug=enabled;
    }
    
    public void info(String msg,Object arg0, Object arg1)
    {
        String d= dateFormat.format(new Date());
        stream.println(d+"  INFO ["+Thread.currentThread().getName()+"] "+name+":"+format(msg,arg0,arg1));
    }
    
    public void debug(String msg,Throwable th)
    {
        if (debug)
        {
            String d= dateFormat.format(new Date());
            stream.println(d+"  DEBUG ["+Thread.currentThread().getName()+"] "+name+":"+msg);
            if (th!=null) th.printStackTrace(stream);
        }
    }
    
    public void debug(String msg,Object arg0, Object arg1)
    {
        if (debug)
        {
            String d= dateFormat.format(new Date());
            stream.println(d+"  DEBUG ["+Thread.currentThread().getName()+"] "+name+":"+format(msg,arg0,arg1));
        }
    }
    
    public void warn(String msg,Object arg0, Object arg1)
    {
        String d= dateFormat.format(new Date());
        stream.println(d+"  WARN ["+Thread.currentThread().getName()+"] "+name+":"+format(msg,arg0,arg1));
    }
    
    public void warn(String msg, Throwable th)
    {
        String d= dateFormat.format(new Date());
        stream.println(d+"  WARN ["+Thread.currentThread().getName()+"] "+name+":"+msg);
        if (th!=null)
            th.printStackTrace(stream);
    }

    private String format(String msg, Object arg0, Object arg1)
    {
        int i0=msg.indexOf("{}");
        int i1=i0<0?-1:msg.indexOf("{}",i0+2);
        
        if (arg1!=null && i1>=0)
            msg=msg.substring(0,i1)+arg1+msg.substring(i1+2);
        if (arg0!=null && i0>=0)
            msg=msg.substring(0,i0)+arg0+msg.substring(i0+2);
        return msg;
    }
    
    public Logger getLogger(String name)
    {
        if ((name==null && this.name==null) ||
            (name!=null && name.equals(this.name)))
            return this;
        return new SeyconLog(name);
    }
    
    public String toString()
    {
        return "SEYCONLOG"+name;
    }

    public static boolean isDebug() {
        return debug;
    }

    public static void setDebug(boolean debug) {
        SeyconLog.debug = debug;
    }

    public static PrintStream getStream() {
        return stream;
    }

    public static void setStream(PrintStream newStream) {
        stream = newStream;
    }

}
