package es.caib.seycon.ng.sync.jetty;

import java.io.PrintStream;

import org.mortbay.log.Logger;
import org.mortbay.util.DateCache;

public class SeyconLog implements Logger {
    private static DateCache _dateCache;
    private static boolean debug = false;
    private String name;
    private static PrintStream stream = System.err;
    
    static
    {
        try
        {
            _dateCache=new DateCache("yyyy-MM-dd HH:mm:ss");
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
        String d=_dateCache.now();
        int ms=_dateCache.lastMs();
        stream.println(d+(ms>99?".":(ms>0?".0":".00"))+ms+":"+name+":INFO:  "+format(msg,arg0,arg1));
    }
    
    public void debug(String msg,Throwable th)
    {
        if (debug)
        {
            String d=_dateCache.now();
            int ms=_dateCache.lastMs();
            stream.println(d+(ms>99?".":(ms>0?".0":".00"))+ms+":"+name+":DEBUG: "+msg);
            if (th!=null) th.printStackTrace(stream);
        }
    }
    
    public void debug(String msg,Object arg0, Object arg1)
    {
        if (debug)
        {
            String d=_dateCache.now();
            int ms=_dateCache.lastMs();
            stream.println(d+(ms>99?".":(ms>0?".0":".00"))+ms+":"+name+":DEBUG: "+format(msg,arg0,arg1));
        }
    }
    
    public void warn(String msg,Object arg0, Object arg1)
    {
        String d=_dateCache.now();
        int ms=_dateCache.lastMs();
        stream.println(d+(ms>99?".":(ms>0?".0":".00"))+ms+":"+name+":WARN:  "+format(msg,arg0,arg1));
    }
    
    public void warn(String msg, Throwable th)
    {
        String d=_dateCache.now();
        int ms=_dateCache.lastMs();
        stream.println(d+(ms>99?".":(ms>0?".0":".00"))+ms+":"+name+":WARN:  "+msg);
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
