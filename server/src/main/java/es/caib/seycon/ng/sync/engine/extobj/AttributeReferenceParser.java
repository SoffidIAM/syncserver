package es.caib.seycon.ng.sync.engine.extobj;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import es.caib.seycon.ng.sync.intf.ExtensibleObject;

public class AttributeReferenceParser {
	final static Pattern p0 = Pattern.compile("\\s*(\\d+)\\s*");
	final static Pattern p0b = Pattern.compile("\\s*(\\d*\\.\\d+)\\s*");
	final static Pattern p1 = Pattern.compile("\\s*\\[\\s*(\\d+)\\s*\\]\\s*");
	final static Pattern p2 = Pattern.compile("\\s*\\{\\s*\\\"(\\S+)\\\"\\s*\\}\\s*");
	final static Pattern p3 = Pattern.compile("\\s*\\.?\\s*([a-zA-Z]\\w*)\\s*");

	public static AttributeReference parse (ExtensibleObject object, String expression)
	{
		AttributeReference root = new RootAttributeReference(object);
		return parse ( root, expression);
	}

	private static AttributeReference parse(AttributeReference root, String expression) {
		AttributeReference ref ;

		if (expression.equals("true"))
			return new ConstantReference(expression, Boolean.TRUE);
		else if (expression.equals("false"))
			return new ConstantReference(expression, Boolean.FALSE);

		Matcher m = p0b.matcher(expression);
		if (m.lookingAt())
		{
			double d = Double.valueOf(m.group(1));
			ref = new ConstantReference(m.group(1), d);
			ref.setParentReference(root);
		} else {
			m = p0.matcher(expression);
			if (m.lookingAt())
			{
				int index = Integer.valueOf(m.group(1));
				ref = new ConstantReference(m.group(1), index);
				ref.setParentReference(root);
			} else {
				m = p1.matcher(expression);
				if (m.lookingAt())
				{
					int index = Integer.valueOf(m.group(1));
					ref = new ArrayAttributeReference(index);
					ref.setParentReference(root);
				} else {
					m = p2.matcher(expression);
					if (m.lookingAt())
					{
						String member = m.group(1);
						ref = new MemberAttributeReference(member);
						ref.setParentReference(root);
					} else {
						m = p3.matcher(expression);
						if (m.lookingAt())
						{
							String member = m.group(1);
							ref = new MemberAttributeReference(member);
							ref.setParentReference(root);
						} else {
							throw new RuntimeException("Unable to parse "+expression);
						}
					}
				}
			}
		}
		
		if (m.end() >= expression.length())
			return ref;
		else
			return parse (ref, expression.substring(m.end()));
	}
}
