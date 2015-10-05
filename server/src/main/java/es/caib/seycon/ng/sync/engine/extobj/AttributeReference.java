package es.caib.seycon.ng.sync.engine.extobj;

import com.soffid.iam.sync.engine.extobj.ArrayAttributeReference;
import com.soffid.iam.sync.engine.extobj.ConstantReference;
import com.soffid.iam.sync.engine.extobj.MemberAttributeReference;
import com.soffid.iam.sync.engine.extobj.RootAttributeReference;

public class AttributeReference {
	protected com.soffid.iam.sync.engine.extobj.AttributeReference delegate ;

	public AttributeReference getParentReference() {
		return toAttributeReference(
				delegate.getParentReference());
	}

	public AttributeReference(
			com.soffid.iam.sync.engine.extobj.AttributeReference delegate) {
		super();
		this.delegate = delegate;
	}

	public void setValue(Object value) {
		delegate.setValue(value);
	}

	public Object getValue() {
		return delegate.getValue();
	}

	public String getName() {
		return delegate.getName();
	}

	public String toString() {
		return delegate.toString();
	}
	
	public static AttributeReference toAttributeReference (com.soffid.iam.sync.engine.extobj.AttributeReference other)
	{
		if (other == null)
			return null;
		else if (other instanceof ArrayAttributeReference)
			return new es.caib.seycon.ng.sync.engine.extobj.ArrayAttributeReference (other);
		else if (other instanceof ConstantReference)
			return new es.caib.seycon.ng.sync.engine.extobj.ConstantReference (other);
		else if (other instanceof MemberAttributeReference)
			return new es.caib.seycon.ng.sync.engine.extobj.MemberAttributeReference (other);
		else if (other instanceof RootAttributeReference)
			return new es.caib.seycon.ng.sync.engine.extobj.RootAttributeReference (other);
		else
			return new AttributeReference(other);
	}
	

}
