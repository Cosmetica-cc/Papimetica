public class PlayerInfo {
	private long timestamp;
	public String username;
	public String uuid;
	public String lore = "";
	public String prefix = "";
	public String suffix = "";
	public boolean slim = false;
	public boolean active = false;
	public CosmeticInformation cape = new CosmeticInformation();
	public CosmeticInformation hat = new CosmeticInformation();
	public CosmeticInformation shoulderBuddy = new CosmeticInformation();

	public PlayerInfo(String username, String uuid) {
		this.username = username;
		this.uuid = uuid;
	}

	public PlayerInfo(String username, String uuid, String lore, String prefix, String suffix, boolean isSlim, boolean isActive, CosmeticInformation cape, CosmeticInformation hat, CosmeticInformation shouderBuddy) {
		this.timestamp = System.currentTimeMillis();
		this.username = username;
		this.uuid = uuid;
		this.lore = lore;
		this.prefix = prefix;
		this.suffix = suffix;
		this.slim = isSlim;
		this.active = isActive;
		this.cape = cape;
		this.hat = hat;
		this.shoulderBuddy = shouderBuddy;
	}

	public boolean needsUpdating() {
		return System.currentTimeMillis() - timestamp > CosmeticaPlaceholderApiExpansion.refreshTime() * 60 * 1000;
	}

	public boolean isExpired() {
		return System.currentTimeMillis() - CosmeticaPlaceholderApiExpansion.expireTime() > 2 * 60 * 60 * 1000;
	}
}
