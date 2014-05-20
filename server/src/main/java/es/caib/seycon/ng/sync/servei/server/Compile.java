package es.caib.seycon.ng.sync.servei.server;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class Compile {
        public int getVersion ()
        {
            return 2;
        }
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			new Compile().parse(args[0], args[1]);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void parse (String sourceFile, String targetFile) throws SAXException, DocumentException, IOException
	{
		parse ( new FileInputStream(sourceFile), new FileOutputStream(targetFile));
	}
	
	public void parse (InputStream sourceFile, OutputStream targetFile) throws SAXException, DocumentException, IOException {
		parse(sourceFile, targetFile, true);
	}
	
	public void parse (InputStream sourceFile, OutputStream targetFile, boolean validate) throws SAXException, DocumentException, IOException
	{
//        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

        // turn validation on
        org.dom4j.io.SAXReader reader = new org.dom4j.io.SAXReader(validate);
        // set the validation feature to true to report validation errors
        reader.setFeature("http://xml.org/sax/features/validation", true);

        // set the validation/schema feature to true to report validation errors
        // against a schema
        reader.setFeature("http://apache.org/xml/features/validation/schema",
        		true);
        // set the validation/schema-full-checking feature to true to enable
        // full schema, grammar-constraint checking
        reader
                .setFeature(
                        "http://apache.org/xml/features/validation/schema-full-checking",
                        true);
        // set the schema
        reader
                .setProperty(
                        "http://apache.org/xml/properties/schema/external-noNamespaceSchemaLocation",
                        "/es/caib/seycon/mazinger/Mazinger.xsd");
        // set the entity resolver (to load the schema with getResourceAsStream)
        reader.getXMLReader().setEntityResolver(new SchemaLoader());
        reader.setEntityResolver(new SchemaLoader());

        
        Document doc = reader.read(sourceFile);
        
        doCompile (doc, targetFile);
	}


    private void doCompile(Document doc, OutputStream outStream) throws IOException {
		DataOutputStream out = new DataOutputStream( outStream );
		// Magic bytes
		out.write("MZN".getBytes("UTF-8"));
		// Version
		out.writeInt(getVersion());
		// Número de aplicaciones
		List elements = doc.getRootElement().elements();
		int validElements = 0;
                for (Iterator it = elements.iterator(); it.hasNext(); )
                {
                    Element element = (Element) it.next();
                    if (isValidElement(element))
                        validElements ++;
                }
		out.writeInt (validElements);
		for (Iterator it = elements.iterator(); it.hasNext(); )
		{
			Element element = (Element) it.next();
			if (isValidElement(element))
			    compileElement(out, element);
		}
    }

    public boolean isValidElement(Element element) 
    {
        if ("Action".equals(element.getName()) ||
            "Application".equals(element.getName()) ||
            "DomainPassword".equals(element.getName()) )
            return true;
        else
            return false;
    }

    public void compileElement(DataOutputStream out, Element element)
            throws IOException {
        if ("Action".equals(element.getName()))
        {
        	doCompileAction(element, out);
        } 
        else if ("Application".equals(element.getName()))
        {
        	doCompileApplication (element, out);
        } 
        else if ("DomainPassword".equals(element.getName()))
        {
        	doCompileDomainPassword (element, out);
        } else {
            throw new IOException ("Invalid tag "+element.getName());
        }
    }


	private void doCompileApplication(Element javaApplication, DataOutputStream out) throws IOException {
		out.write('J');
		Attribute name = javaApplication.attribute("cmdLine"); 
		if (name != null)
		{
			out.write('N');
			out.write(name.getValue().getBytes("UTF-8"));
			out.write(0);
		}
		out.write(0);
		List components = javaApplication.elements();
		out.writeInt (components.size());
		for (Iterator it = components.iterator(); it.hasNext(); )
		{
			Element component = (Element) it.next();
			if ("Component".equals(component.getName()))
			{
				doCompileComponent (component, out);
			} 
			else 
			{
				throw new IOException ("Unknown tag "+javaApplication.getName());
			}
		}
	}

	private void doCompileDomainPassword(Element domainPassword, DataOutputStream out) throws IOException {
		out.write('P');
		compileAttribute(domainPassword, "domain", 'D', out);
		compileAttribute(domainPassword, "servers", 'S', out);
		compileAttribute(domainPassword, "userSecret", 'U', out);
		compileAttribute(domainPassword, "passwordSecret", 'P', out);
		out.write(0);
		List actions = domainPassword.elements("Action");
		out.writeInt (actions.size());
		for (Iterator it = actions.iterator(); it.hasNext(); )
		{
			Element action = (Element) it.next();
			doCompileAction (action, out);
		}
	}



	private void doCompileComponent(Element component, DataOutputStream out) throws IOException {
		out.write('j');
		if (component.attribute("check") != null
				&& "partial".equals(component.attribute("check").getValue()))
			out.write('P');
		else
			out.write('F');
		compileAttribute(component, "name", 'N', out);
		compileAttribute(component, "class", 'C', out);
		compileAttribute(component, "title", 'T', out);
		compileAttribute(component, "text", 'V', out);
		compileAttribute(component, "dlgId", 'D', out);
		compileAttribute(component, "id", 'I', out);
                compileAttribute(component, "ref-as", 'I', out);
		compileAttribute(component, "optional", 'O', out);
		out.write(0);
		List actions = component.elements("Action");
		out.writeInt (actions.size());
		for (Iterator it = actions.iterator(); it.hasNext(); )
		{
			Element action = (Element) it.next();
			doCompileAction (action, out);
		}
		List components = component.elements("Component");
		out.writeInt (components.size());
		for (Iterator it = components.iterator(); it.hasNext(); )
		{
			Element childComponent = (Element) it.next();
			doCompileComponent (childComponent, out);
		}
	}

	protected void doCompileAction(Element action, DataOutputStream out) throws IOException {
		out.write('A');
		compileAttribute(action, "type", 'T', out);
		compileAttribute(action, "text", 't', out);
		compileAttribute(action, "repeat", 'R', out);
		compileAttribute(action, "delay", 'D', out);
		out.write(0);
		out.write(action.getText().getBytes("UTF-8"));
		out.write(0);
	}

	public void compileAttribute(Element component, String attributeName, char discriminator, DataOutputStream out)
			throws IOException {
		Attribute attribute = component.attribute(attributeName); 
		if (attribute != null)
		{
			out.write(discriminator);
			out.write(attribute.getValue().getBytes("UTF-8"));
			out.write(0);
		}
	}


	public class SchemaLoader implements EntityResolver {
        public static final String FILE_SCHEME = "file://";

        public InputSource resolveEntity(String publicId, String systemId)
                throws SAXException {
        	InputStream input = SchemaLoader.class
            	.getResourceAsStream("/es/caib/seycon/mazinger/Mazinger.xsd");
            return new InputSource(input);
        }
    }

    static final String JAXP_SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";
    static final String W3C_XML_SCHEMA = "http://www.w3.org/2001/XMLSchema";
    static final String JAXP_SCHEMA_SOURCE = "http://java.sun.com/xml/jaxp/properties/schemaSource";


}
