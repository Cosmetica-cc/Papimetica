public class CosmeticInformation {
	public String id = "";
	public String name = "";
	public String owner = "";
	public String origin = "";
	public boolean isFromCosmetica = false;

	public CosmeticInformation() {}

	public CosmeticInformation(String id, String name, String owner, String origin, boolean isFirstParty) {
		this.id = id;
		this.name = name;
		this.owner = owner;
		this.origin = origin;
		this.isFromCosmetica = isFirstParty;
	}
}
