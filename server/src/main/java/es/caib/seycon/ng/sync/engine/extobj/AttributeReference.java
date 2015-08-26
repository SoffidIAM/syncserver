package es.caib.seycon.ng.sync.engine.extobj;

public class AttributeReference {
	com.soffid.iam.sync.engine.extobj.AttributeReference delegate ;

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
}
