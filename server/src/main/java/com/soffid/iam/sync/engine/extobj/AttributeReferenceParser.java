package com.soffid.iam.sync.engine.extobj;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.soffid.iam.sync.intf.ExtensibleObject;

public class AttributeReferenceParser {
	final static Pattern pattern_number = Pattern.compile("\\s*(\\d+)\\s*");
	final static Pattern pattern_decimal = Pattern.compile("\\s*(\\d*\\.\\d+)\\s*");
	final static Pattern pattern_string = Pattern.compile("\\s*\"(.*)\"\\s*");
	final static Pattern pattern_string2 = Pattern.compile("\\s*\'(.*)\'\\s*");
	final static Pattern pattern_array = Pattern.compile("\\s*\\[\\s*(\\d+)\\s*\\]\\s*");
	final static Pattern pattern_member = Pattern.compile("\\s*\\{\\s*\\\"(\\S+)\\\"\\s*\\}\\s*");
	final static Pattern pattern_dotmember = Pattern.compile("\\s*\\.?\\s*([a-zA-Z]\\w*)\\s*");
	final static Pattern pattern_arraymember = Pattern.compile("\\s*\\[\\s*\\\"(\\S+)\\\"\\s*\\]\\s*");
	final static Pattern pattern_arraymember2 = Pattern.compile("\\s*\\[\\s*\\\"(\\S+)\\\"\\s*\\]\\s*");

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

		Matcher m = pattern_decimal.matcher(expression);
		if (m.matches())
		{
			double d = Double.valueOf(m.group(1));
			ref = new ConstantReference(m.group(1), d);
			ref.setParentReference(root);
		} else {
			m = pattern_number.matcher(expression);
			if (m.matches())
			{
				int index = Integer.valueOf(m.group(1));
				ref = new ConstantReference(m.group(1), index);
				ref.setParentReference(root);
			} else {
				m = pattern_string.matcher(expression);
				if (m.matches())
				{
					ref = new ConstantReference(m.group(1), unquote('\"', m.group(1)));
					ref.setParentReference(root);
				} else {
					m = pattern_string2.matcher(expression);
					if (m.matches())
					{
						ref = new ConstantReference(m.group(1), unquote('\'', m.group(1)));
						ref.setParentReference(root);
					} else {
						m = pattern_array.matcher(expression);
						if (m.lookingAt())
						{
							int index = Integer.valueOf(m.group(1));
							ref = new ArrayAttributeReference(index);
							ref.setParentReference(root);
						} else {
							m = pattern_member.matcher(expression);
							if (m.lookingAt())
							{
								String member = m.group(1);
								ref = new MemberAttributeReference(member);
								ref.setParentReference(root);
							} else {
								m = pattern_arraymember.matcher(expression);
								if (m.lookingAt())
								{
									String member = m.group(1);
									ref = new MemberAttributeReference(member);
									ref.setParentReference(root);
								} else {
									m = pattern_arraymember2.matcher(expression);
									if (m.lookingAt())
									{
										String member = m.group(1);
										ref = new MemberAttributeReference(member);
										ref.setParentReference(root);
									} else {
										m = pattern_dotmember.matcher(expression);
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
					}
				}
			}
		}
		
		if (m.end() >= expression.length())
			return ref;
		else
			return parse (ref, expression.substring(m.end()));
	}

	private static String unquote(char quote, String group) {
		StringBuffer sb = new StringBuffer();
		boolean backSlash = false;
		for (char ch: group.toCharArray()) {
			if (backSlash) {
				switch (ch) {
				case 'r': sb.append('\r'); break;
				case 'n': sb.append('\n'); break;
				case 't': sb.append('\t'); break;
				case '0': sb.append('\0'); break;
				default: sb.append(ch);
				}
				backSlash = false;
			}
			else if (ch == '\\') backSlash = true;
			else if (ch == quote) throw new RuntimeException("Unable to parse string "+quote+group+quote);
			else sb.append(ch);
		}
		return sb.toString();
	}
}
