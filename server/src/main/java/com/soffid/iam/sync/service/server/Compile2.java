package com.soffid.iam.sync.service.server;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.dom4j.Attribute;
import org.dom4j.Element;

public class Compile2 extends Compile {

    public boolean isValidElement(Element element) {
        if ("WebApplication".equals(element.getName()))
            return true;
        else
            return super.isValidElement(element);
    }

    public void compileElement(DataOutputStream out, Element element)
            throws IOException {
        if ("WebApplication".equals(element.getName())) {
            doCompileWebApplication(element, out);
        } else if ("HllApplication".equals(element.getName())) {
            // Ignore hll applications for version 2
        } else {
            super.compileElement(out, element);
        }
    }

    private void doCompileWebApplication(Element webApplication,
            DataOutputStream out) throws IOException {

        out.write('W');
        compileAttribute(webApplication, "url", 'U', out);
        compileAttribute(webApplication, "title", 'T', out);
        compileAttribute(webApplication, "content", 'C', out);
        out.write(0);
        List inputs = webApplication.elements("Input");
        out.writeInt(inputs.size());
        for (Iterator it = inputs.iterator(); it.hasNext();) {
            Element e = (Element) it.next();
            doCompileInput(e, out);
        }
        List forms = webApplication.elements("Form");
        out.writeInt(forms.size());
        for (Iterator it = forms.iterator(); it.hasNext();) {
            Element e = (Element) it.next();
            doCompileForm(e, out);
        }
        List actions = webApplication.elements("Action");
        out.writeInt(actions.size());
        for (Iterator it = actions.iterator(); it.hasNext();) {
            Element e = (Element) it.next();
            doCompileAction(e, out);
        }
    }

    private void doCompileInput(Element component, DataOutputStream out) throws IOException {
        out.write('i');
        compileAttribute(component, "type", 'T', out);
        compileAttribute(component, "value", 'V', out);
        compileAttribute(component, "name", 'N', out);
        compileAttribute(component, "id", 'I', out);
        compileAttribute(component, "ref-as", 'R', out);
        compileAttribute(component, "optional", 'O', out);
        out.write(0);
    }

    private void doCompileForm(Element component, DataOutputStream out) throws IOException {
        out.write('f');
        compileAttribute(component, "action", 'A', out);
        compileAttribute(component, "method", 'M', out);
        compileAttribute(component, "name", 'N', out);
        compileAttribute(component, "id", 'I', out);
        compileAttribute(component, "ref-as", 'R', out);
        compileAttribute(component, "optional", 'O', out);
        out.write(0);
        List inputs = component.elements("Input");
        out.writeInt (inputs.size());
        for (Iterator it = inputs.iterator(); it.hasNext(); )
        {
                Element input = (Element) it.next();
                doCompileInput (input, out);
        }
}


    protected void doCompileAction(Element action, DataOutputStream out) throws IOException {
        out.write('A');
        compileAttribute(action, "type", 'T', out);
        compileAttribute(action, "text", 't', out);
        compileAttribute(action, "repeat", 'R', out);
        compileAttribute(action, "event", 'E', out);
        compileAttribute(action, "delay", 'D', out);
        out.write(0);
        out.write(action.getText().getBytes("UTF-8"));
        out.write(0);
    }

    @Override
    public int getVersion() {
        return 3;
    }
    
    

}
