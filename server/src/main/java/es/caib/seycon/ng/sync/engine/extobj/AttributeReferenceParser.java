package es.caib.seycon.ng.sync.engine.extobj;

import com.soffid.iam.sync.engine.extobj.MemberAttributeReference;

import es.caib.seycon.ng.sync.intf.ExtensibleObject;

public class AttributeReferenceParser {
	com.soffid.iam.sync.engine.extobj.AttributeReferenceParser delegate;
	
	public static AttributeReference parse (ExtensibleObject object, String expression)
	{
		return AttributeReference.toAttributeReference(com.soffid.iam.sync.engine.extobj.AttributeReferenceParser.parse ( object, expression));
	}
	
}
