package com.soffid.iam.sync.jetty;

import java.text.DateFormat;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import org.mortbay.util.DateCache;

public class Formatter extends SimpleFormatter {
    @Override
    public synchronized String format(LogRecord record) {
        
        return String.format ("%1$tY-%1$tm-%1td %1$tH:%1$tM:%1$tS.%1$tL:%2$s:%3$s:%4$s\n",
                record.getMillis(),
                record.getLoggerName(),
                record.getLevel(),
                record.getMessage());
    }

    
}
