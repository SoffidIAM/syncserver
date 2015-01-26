package bubu.test.seycon;

import java.util.List;
import java.util.Map;

import junit.framework.TestCase;
import es.caib.seycon.ng.sync.engine.extobj.AttributeReferenceParser;
import es.caib.seycon.ng.sync.intf.ExtensibleObject;

public class AttParserTest extends TestCase {

	private void dump (String prefix, Map<String, Object> obj)
	{
		for (String s: obj.keySet())
		{
			Object v = obj.get(s);
			if (v instanceof Map)
			{
				System.out.println (prefix+s+":");
				dump (prefix+ "  ", (Map<String,Object>)v);
			}
			else if (v instanceof List)
			{
				System.out.println (prefix+s+":");
				dump (prefix+ "  ", (List<Object>)v);
			}
			else if (v == null)
				System.out.println (prefix+s+":<null>");
			else
				System.out.println (prefix+s+":"+v.toString());
		}
	}

	private void dump (String prefix, List<Object> obj)
	{
		int i = 0;
		for (Object v: obj)
		{
			if (v instanceof Map)
			{
				System.out.println (prefix+"["+i+"]:");
				dump (prefix+ "  ", (Map<String,Object>)v);
			}
			else if (v instanceof List)
			{
				System.out.println (prefix+"["+i+"]:");
				dump (prefix+ "  ", (List<Object>)v);
			}
			else if (v == null)
				System.out.println (prefix+"["+i+"]:<null>");
			else
				System.out.println (prefix+"["+i+"]:"+v.toString());
			i ++;
		}
	}

	public void testSimpleAssign ()
	{
		ExtensibleObject eo = new ExtensibleObject();
		AttributeReferenceParser.parse(eo, "att").setValue("Test1");
		
		dump ("" , eo);
	}

	public void testComplexAssign ()
	{
		ExtensibleObject eo = new ExtensibleObject();
		AttributeReferenceParser.parse(eo, "att").setValue("Test1");
		AttributeReferenceParser.parse(eo, "att1.att2").setValue("Test2");
		AttributeReferenceParser.parse(eo, "att1{\"att3\"}").setValue("Test3");
		AttributeReferenceParser.parse(eo, "att2[0]").setValue("Test4");
		AttributeReferenceParser.parse(eo, "att2[2]").setValue("Test5");
		AttributeReferenceParser.parse(eo, "att2[2]").getValue();
		assertEquals(AttributeReferenceParser.parse(eo, "10").getValue(), 10);
		assertEquals(AttributeReferenceParser.parse(eo, "10.12").getValue(), 10.12);
		assertEquals(AttributeReferenceParser.parse(eo, "true").getValue(), true);
		assertEquals(AttributeReferenceParser.parse(eo, "false").getValue(), false);
		boolean ok = false;
		try {
			AttributeReferenceParser.parse(eo, "if (att2[2] == null) null;").getValue();
			ok = true;
		} catch (RuntimeException e) {
			System.out.println ("Received expected exceptin: "+e );
		}
		if (ok )
			throw new AssertionError("Attribute parser did no throw exception");
		dump ("" , eo);
	}
}
