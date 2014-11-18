package es.caib.seycon.ng.sync.engine.extobj;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import es.caib.seycon.ng.sync.intf.ExtensibleObject;

public class AttributeReferenceParser {
	final static Pattern p1 = Pattern.compile("\\s*\\[\\s*(\\d+)\\s*\\]\\s*");
	final static Pattern p2 = Pattern.compile("\\s*\\{\\s*\\\"(\\S+)\\\"\\s*\\}\\s*");
	final static Pattern p3 = Pattern.compile("\\s*\\.?\\s*(\\w+)\\s*");

	public static AttributeReference parse (ExtensibleObject object, String expression)
	{
		AttributeReference root = new RootAttributeReference(object);
		return parse ( root, expression);
	}

	private static AttributeReference parse(AttributeReference root, String expression) {
		AttributeReference ref ;
		
		Matcher m = p1.matcher(expression);
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
		
		if (m.end() >= expression.length())
			return ref;
		else
			return parse (ref, expression.substring(m.end()));
	}
}
