package es.caib.seycon.ng.sync.engine.extobj;

public class ConstantReference extends AttributeReference {
	String name;
	Object value;
	
	
	public ConstantReference(String name, Object value) {
		super();
		this.name = name;
		this.value = value;
	}

	@Override
	public void setValue(Object value) {
		throw new RuntimeException ("Cannot assign value to constant expression "+name);
	}

	@Override
	public Object getValue() {
		return value;
	}

	@Override
	public String getName() {
		return name;
	}

}
