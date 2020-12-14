package com.soffid.iam.sync.service.server;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.dom4j.Attribute;
import org.dom4j.Element;

public class Compile4 extends Compile3 {

    public boolean isValidElement(Element element) {
        if ("WebTransport".equals(element.getName()))
            return true;
        else
            return super.isValidElement(element);
    }

    public void compileElement(DataOutputStream out, Element element)
            throws IOException {
        if ("WebTransport".equals(element.getName())) {
            doCompileWebTransport(element, out);
        } else {
            super.compileElement(out, element);
        }
    }

    @SuppressWarnings ("rawtypes")
	private void doCompileWebTransport(Element webApplication,
            DataOutputStream out) throws IOException {

        out.write('T');
        compileAttribute(webApplication, "url", 'U', out);
        compileAttribute(webApplication, "system", 'S', out);
        compileAttribute(webApplication, "domain", 'D', out);
        out.write(0);
    }


    @Override
    public int getVersion() {
        return 4;
    }
    
    

}
