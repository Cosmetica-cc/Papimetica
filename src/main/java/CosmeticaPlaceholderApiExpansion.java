import com.google.common.io.Files;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class CosmeticaPlaceholderApiExpansion extends PlaceholderExpansion {
	private static final JsonParser PARSER = new JsonParser();
	private static Map<Player, PlayerInfo> playerInfoList = new HashMap<>();
	private List<Player> lookingUpUsers = new LinkedList<>();
	private static boolean removeEntriesStarted = false;
	FileConfiguration config;
	private static long dataRefreshTime;
	private static long dataExpiredTime;
	private static String serverIp;
	private static boolean isEnabled = false;

	public CosmeticaPlaceholderApiExpansion() {
		isEnabled = false;
		try {
			File configFile = new File(PlaceholderAPIPlugin.getInstance().getDataFolder(), "cosmetica.yml");
			if (!configFile.exists()) {
				StringBuilder builder = new StringBuilder();
				try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("cosmetica-config.yml")))) {
					String str;
					while ((str = bufferedReader.readLine()) != null) {
						builder.append(str).append("\n");
					}
					try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile.getPath()), StandardCharsets.UTF_8))) {
						writer.write(builder.toString());
					}
				}
			}
			config = YamlConfiguration.loadConfiguration(configFile);
			dataRefreshTime = config.contains("data-refresh-period") ? config.getLong("data-refresh-period") : 30;
			dataExpiredTime = config.contains("data-expire-period") ? config.getLong("data-expire-period") : 60;
			String tempServerIp = config.contains("server-ip") ? config.getString("server-ip") : "127.0.0.1:25565";
			serverIp = Base64.getEncoder().encodeToString(tempServerIp.getBytes());
			isEnabled = true;
		} catch (IOException e) {
			System.out.println("ERROR!");
			e.printStackTrace();
			isEnabled = false;
		}
		//config = YamlConfiguration.loadConfiguration(new File(PlaceholderAPIPlugin.getInstance().getDataFolder(), "cosmetica.yml"));
	}

	public static boolean isEnabled() {
		return isEnabled;
	}

	public static long expireTime() {
		return dataExpiredTime;
	}

	public static long refreshTime() {
		return dataRefreshTime;
	}

	public static String getServerIp() {
		return serverIp;
	}

	private void removeOldEntries() {
		removeEntriesStarted = true;
		Thread thread = new Thread(() -> {
			for (Player player : playerInfoList.keySet()) {
				if (playerInfoList.get(player).isExpired()) playerInfoList.remove(player);
			}
			try {
				Thread.sleep(30 * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});
		thread.start();
	}

	@Override
	public String getIdentifier() {
		return "Cosmetica";
	}

	@Override
	public boolean canRegister() {
		return true;
	}

	@Override
	public String getAuthor() {
		return "eyezah";
	}

	@Override
	public String getVersion() {
		return "1.0.0";
	}

	@Override
	public String onPlaceholderRequest(Player p, String identifier) {
		if (!removeEntriesStarted) removeOldEntries();
		if (identifier.equals("active")) return getUserData(p).active ? "True" : "False";
		if (identifier.equals("lore")) return getUserData(p).lore;
		if (identifier.equals("prefix")) return getUserData(p).prefix;
		if (identifier.equals("suffix")) return getUserData(p).suffix;
		if (identifier.equals("cape_exists")) return getUserData(p).cape.id.isEmpty() ? "False" : "True";
		if (identifier.equals("cape_id")) return getUserData(p).cape.id;
		if (identifier.equals("cape_name")) return getUserData(p).cape.name;
		if (identifier.equals("cape_owner")) return getUserData(p).cape.owner;
		if (identifier.equals("cape_origin")) return getUserData(p).cape.origin;
		if (identifier.equals("cape_isfromcosmetica")) return getUserData(p).cape.isFromCosmetica ? "True" : "False";
		if (identifier.equals("hat_exists")) return getUserData(p).hat.id.isEmpty() ? "False" : "True";
		if (identifier.equals("hat_id")) return getUserData(p).hat.id;
		if (identifier.equals("hat_name")) return getUserData(p).hat.name;
		if (identifier.equals("hat_owner")) return getUserData(p).hat.owner;
		if (identifier.equals("hat_origin")) return getUserData(p).hat.origin;
		if (identifier.equals("hat_isfromcosmetica")) return getUserData(p).hat.isFromCosmetica ? "True" : "False";
		if (identifier.equals("shoulderbuddy_exists")) return getUserData(p).shoulderBuddy.id.isEmpty() ? "False" : "True";
		if (identifier.equals("shoulderbuddy_id")) return getUserData(p).shoulderBuddy.id;
		if (identifier.equals("shoulderbuddy_name")) return getUserData(p).shoulderBuddy.name;
		if (identifier.equals("shoulderbuddy_owner")) return getUserData(p).shoulderBuddy.owner;
		if (identifier.equals("shoulderbuddy_origin")) return getUserData(p).shoulderBuddy.origin;
		if (identifier.equals("shoulderbuddy_isfromcosmetica")) return getUserData(p).shoulderBuddy.isFromCosmetica ? "True" : "False";

		return null;
	}

	public PlayerInfo getUserData(Player p) {
		if (p == null) return new PlayerInfo("", "");
		if (playerInfoList.containsKey(p) && !playerInfoList.get(p).needsUpdating()) {
			return playerInfoList.get(p);
		} else {
			if (!lookingUpUsers.contains(p)) {
				Thread thread = new Thread(() -> {
					lookingUpUsers.add(p);
					PlayerInfo playerInfo = loadDataForUser(p.getName(), p.getUniqueId().toString());
					if (playerInfo != null) playerInfoList.put(p, playerInfo);
					lookingUpUsers.remove(p);
				});
				thread.start();
			}
			return playerInfoList.containsKey(p) ? playerInfoList.get(p) : new PlayerInfo(p.getName(), p.getUniqueId().toString());
		}
	}

	public PlayerInfo loadDataForUser(String username, String uuid) {
		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			final HttpGet httpGet = new HttpGet("http://api.cosmetica.cc/get/info?excludemodels&username=" + urlEncode(username) + "&uuid=" + urlEncode(uuid));
			try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
				JsonObject jsonObject = PARSER.parse(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8).trim()).getAsJsonObject();
				String lore = getStringFromJson(jsonObject, "lore");
				String prefix = getStringFromJson(jsonObject, "prefix");
				String suffix = getStringFromJson(jsonObject, "suffix");
				boolean isSlim = jsonObject.has("slim") && jsonObject.get("slim").getAsBoolean();
				CosmeticInformation cape = jsonObject.has("cape") ? getCosmeticInfo(jsonObject.get("cape")) : new CosmeticInformation();
				CosmeticInformation hat = jsonObject.has("hat") ? getCosmeticInfo(jsonObject.get("cape")) : new CosmeticInformation();
				CosmeticInformation shoulderBuddy = jsonObject.has("shoulder-buddy") ? getCosmeticInfo(jsonObject.get("shoulder-buddy")) : new CosmeticInformation();
				boolean isActive = false;
				if (!getServerIp().equals("MTI3LjAuMC4xOjI1NTY1")) { // 127.0.0.1:25565 in base64, default value and ignored by cosmetica so no point in making the call
					try (CloseableHttpClient httpClient2 = HttpClients.createDefault()) {
						final HttpGet httpGet2 = new HttpGet("http://api.cosmetica.cc/get/playeractiveonserver?username=" + urlEncode(username) + "&uuid=" + urlEncode(uuid) + "&ip=" + getServerIp());
						try (CloseableHttpResponse response2 = httpClient2.execute(httpGet2)) {
							JsonObject jsonObject2 = PARSER.parse(EntityUtils.toString(response2.getEntity(), StandardCharsets.UTF_8).trim()).getAsJsonObject();
							isActive = jsonObject2.has("active") && jsonObject2.get("active").getAsBoolean();
						}
					} catch (Exception e) {
						return null;
					}
				}
				return new PlayerInfo(username, uuid, lore, prefix, suffix, isSlim, isActive, cape, hat, shoulderBuddy);
			}
		} catch (Exception e) {
			return null;
		}
	}

	private static CosmeticInformation getCosmeticInfo(JsonElement jsonElement) {
		return getCosmeticInfo(jsonElement.getAsJsonObject());
	}

	private static CosmeticInformation getCosmeticInfo(JsonObject jsonObject) {
		String id = getStringFromJson(jsonObject, "id");
		String name = "";
		String owner = "";
		String origin = "Cosmetica";
		boolean isFirstParty = true;
		if (jsonObject.has("origin")) {
			origin = getStringFromJson(jsonObject, "origin", "Cosmetica");
			if (origin.equals("Cosmetica")) {
				name = getStringFromJson(jsonObject, "name");
				owner = getStringFromJson(jsonObject, "name");
			} else {
				isFirstParty = false;
			}
		}
		return new CosmeticInformation(id, name, owner, origin, isFirstParty);

	}

	private static String getStringFromJson(JsonObject jsonObject, String string) {
		return getStringFromJson(jsonObject, string, "");
	}

	private static String getStringFromJson(JsonObject jsonObject, String string, String defaultString) {
		return (jsonObject.has(string) && jsonObject.get(string).getAsString() != null) ? jsonObject.get(string).getAsString() : defaultString;
	}

	public static String urlEncode(String value) {
		try {
			return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException ex) {
			throw new RuntimeException(ex.getCause());
		}
	}
}
