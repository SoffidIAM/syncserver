package es.caib.seycon.ng.sync.servei.server;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.dom4j.Attribute;
import org.dom4j.Element;

public class Compile3 extends Compile2 {

    public boolean isValidElement(Element element) {
        if ("HllApplication".equals(element.getName()))
            return true;
        else
            return super.isValidElement(element);
    }

    public void compileElement(DataOutputStream out, Element element)
            throws IOException {
        if ("HllApplication".equals(element.getName())) {
            doCompileHllApplication(element, out);
        } else {
            super.compileElement(out, element);
        }
    }

    @SuppressWarnings ("rawtypes")
	private void doCompileHllApplication(Element webApplication,
            DataOutputStream out) throws IOException {

        out.write('H');
        out.write(0);
        List inputs = webApplication.elements("Pattern");
        out.writeInt(inputs.size());
        for (Iterator it = inputs.iterator(); it.hasNext();) {
            Element e = (Element) it.next();
            doCompilePattern(e, out);
        }
        List actions = webApplication.elements("Action");
        out.writeInt(actions.size());
        for (Iterator it = actions.iterator(); it.hasNext();) {
            Element e = (Element) it.next();
            doCompileAction(e, out);
        }
    }

    private void doCompilePattern(Element component, DataOutputStream out) throws IOException {
        out.write('p');
        compileAttribute(component, "row", 'R', out);
        out.write(0);
        out.write(component.getText().getBytes("UTF-8"));
        out.write(0);
        
    }

    @Override
    public int getVersion() {
        return 4;
    }
    
    

}
