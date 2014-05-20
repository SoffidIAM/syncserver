/**
 * 
 */
package bubu.test.seycon;

import java.util.HashMap;
import java.util.LinkedList;

import junit.framework.TestCase;
import bsh.BshMethod;
import bsh.CallStack;
import bsh.EvalError;
import bsh.Interpreter;
import bsh.NameSource;
import bsh.NameSpace;
import bsh.This;
import bsh.UtilEvalError;


/**
 * @author bubu
 *
 */ 
public class BSHTest extends TestCase
{

	public void test () throws EvalError
	{
		Interpreter bsh = new Interpreter();
		bsh.setStrictJava(false);
		
		bsh.set("test", "Hola Caracola");
		HashMap<String, String> m = new HashMap<String, String>();
		m.put("a", "hola");
		bsh.set("m", m);
		Object result = bsh.eval("test");
		System.out.println("Result="+result.toString());
		System.out.println("Class="+result.getClass().getName());
		
		
		result = bsh.eval("m{\"a\"}");
		System.out.println("Result="+result.toString());
		System.out.println("Class="+result.getClass().getName());

		result = bsh.eval("execute() {\"10\";\"20\";;;};m{\"x\"}=execute();");
		System.out.println("Result="+result.toString());
		System.out.println("m[x]="+m.get("x"));
		
		LinkedList<String> list = new LinkedList<String>();
		list.add("Hola");
		bsh.set("list", list);
		result = bsh.eval ("list.get(0)");
		System.out.println("Result="+result.toString());
		
}
 
}

class TestObject extends bsh.NameSpace
{
	@Override
	public String[] getMethodNames ()
	{
		System.out.println ("Called getmethodnames");
		return super.getMethodNames();
	}

	@Override
	public BshMethod[] getMethods ()
	{
		System.out.println ("Called getmethods");
		return super.getMethods();
	}

	@Override
	public BshMethod getMethod (String name, Class[] sig) throws UtilEvalError
	{
		System.out.println ("Called getmethod");
		return super.getMethod(name, sig);
	}

	@Override
	public BshMethod getMethod (String name, Class[] sig, boolean declaredOnly)
					throws UtilEvalError
	{
		System.out.println ("Called getmethod");
		return super.getMethod(name, sig, declaredOnly);
	}

	@Override
	public String[] getAllNames ()
	{
		System.out.println ("Called getallnames");
		return super.getAllNames();
	}

	public TestObject( )
	{
		super ((NameSpace) null, "testObjectNS");
	}
	
	public String invokeMethod ( String methodName, Object [] arguments ) { 
		System.out.println ("Called method "+methodName);
		return null;
	}
	
}