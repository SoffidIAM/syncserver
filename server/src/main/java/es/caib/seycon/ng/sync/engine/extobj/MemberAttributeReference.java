package es.caib.seycon.ng.sync.engine.extobj;

public class MemberAttributeReference extends AttributeReference {

	public MemberAttributeReference(
			com.soffid.iam.sync.engine.extobj.AttributeReference delegate) {
		super(delegate);
	}

	public String getMember()
	{
		return ((com.soffid.iam.sync.engine.extobj.MemberAttributeReference)delegate).getMember();
	}

	public void setMember(String member)
	{
		((com.soffid.iam.sync.engine.extobj.MemberAttributeReference)delegate).setMember(member);
	}


}
