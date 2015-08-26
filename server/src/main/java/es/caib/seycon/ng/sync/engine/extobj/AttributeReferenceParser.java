package es.caib.seycon.ng.sync.engine.extobj;

import es.caib.seycon.ng.sync.intf.ExtensibleObject;

public class AttributeReferenceParser {
	com.soffid.iam.sync.engine.extobj.AttributeReferenceParser delegate;
	
	public static AttributeReference parse (ExtensibleObject object, String expression)
	{
		return new AttributeReference(
				com.soffid.iam.sync.engine.extobj.AttributeReferenceParser.parse ( object, expression));
	}
	
}
