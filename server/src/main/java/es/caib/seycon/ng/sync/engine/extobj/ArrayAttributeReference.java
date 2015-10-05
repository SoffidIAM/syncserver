package es.caib.seycon.ng.sync.engine.extobj;

public class ArrayAttributeReference extends AttributeReference {

	public ArrayAttributeReference(
			com.soffid.iam.sync.engine.extobj.AttributeReference delegate) {
		super(delegate);
	}

	public int getIndex()
	{
		return ((com.soffid.iam.sync.engine.extobj.ArrayAttributeReference)delegate).getIndex();
	}

	public void setIndex(int index)
	{
		((com.soffid.iam.sync.engine.extobj.ArrayAttributeReference)delegate).setIndex(index);
	}


}
