package dev.eventide.client.module;

import dev.eventide.client.compat.Compat;
import dev.eventide.client.Config;
import dev.eventide.client.hud.ScoreboardWidget;
import dev.eventide.client.mixin.PlayerTabOverlayAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Name Protect's Auto: finds what to hide by reading the sidebar, the tab list's header and footer and
 * players' team prefixes, the way any server lays them out, with no per-server rules. A line made of a
 * label and a number ("Kills: 12", "Coins » 1,234", "12 Wins") is a stat, hidden as "Kills: ???" wherever
 * it shows (the number may change, the rule still fits); a domain or IP (and the server you joined) is the
 * server's address; a short capitalised tag in brackets ("[MVP+]") is a rank. Your name (and, with Hide
 * other players, everyone's) is swapped by {@link NameProtect} as before.
 *
 * <p>Everything found becomes a rule for {@link TextRules}, which already changes text wherever Minecraft
 * draws it: scoreboard, tab list, chat, nametags, titles, boss bars, the action bar, books and signs. It
 * only reads again when what it reads from changes (checked once a tick from the objects' identities, so
 * nothing is rebuilt while nothing changes), and drawing just uses the rules it made.
 */
public final class NameDetect {
	/** What a found piece of text is. */
	public enum Kind {
		NAME("Name", "user"),
		NUMBER("Stat", "hash"),
		ADDRESS("Server address", "globe"),
		RANK("Rank tag", "crown");

		public final String label;
		/** A Lucide icon name. */
		public final String icon;

		Kind(String label, String icon) {
			this.label = label;
			this.icon = icon;
		}
	}

	/**
	 * One thing found: {@code key} names it for excluding (it stays the same while the number changes),
	 * {@code shown} is the text as found, {@code find} the rule that matches it (# for any number),
	 * {@code replacement} what it becomes, {@code where} where it was.
	 */
	public record Found(Kind kind, String key, String shown, String find, String replacement, String where, boolean excluded) {
	}

	public static final String HIDDEN_NUMBER = "???";
	public static final String HIDDEN_ADDRESS = "server.ip";
	public static final String HIDDEN_RANK = "[Rank]";
	private static final Pattern LABEL_NUMBER = Pattern.compile(
			"^\\s*(\\p{L}[\\p{L}\\p{N} '’.]{0,22}?)(\\s*[:»>|]\\s*|\\s+)([-+]?\\d[\\d.,]*[kKmMbBtT]?)\\s*[\\p{L}%]{0,6}\\s*$");
	private static final Pattern NUMBER_LABEL = Pattern.compile("^\\s*([-+]?\\d[\\d.,]*[kKmMbBtT]?)\\s+(\\p{L}[\\p{L} ]{1,16})\\s*$");
	private static final Pattern DOMAIN = Pattern.compile(
			"(?i)\\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+(?:com|net|org|gg|club|io|me|eu|us|uk|de|fr|pl|ru|co|xyz|fun|world|games|network|online"
					+ "|pro|tv|cc|nl|es|it|br|ca|au|se|no|fi|dk|be|at|ch|cz|sk|hu|ro|tr|pt|land|live|mc|rocks|lol|top|vip|space|site|host|best|zone|one|app"
					+ "|dev|ninja|team|gay|wtf|red|life)\\b");
	private static final Pattern IP = Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}(?::\\d{2,5})?\\b");
	private static final Pattern RANK = Pattern.compile("[\\[(]([A-Z][A-Z0-9]{0,9}\\+{0,3})[\\])]");

	private static volatile List<Found> found = List.of();
	private static long signature = 0;
	private static int excludedSize = -1;

	private NameDetect() {
	}

	/** Whether Auto is finding things to hide. */
	public static boolean on() {
		return Modules.NAME_PROTECT.enabled() && Modules.NAME_PROTECT_AUTO.get();
	}

	/** What Auto found on screen, newest reading, excluded ones included (marked). */
	public static List<Found> found() {
		return found;
	}

	/** Leaves one found thing as it is (or hides it again). */
	public static void toggleExcluded(Found item) {
		List<String> excluded = Config.get().protectExcluded;
		if (!excluded.remove(item.key())) excluded.add(item.key());
		Config.saveSoon();
		signature = 0;
	}

	static void tick(Minecraft mc) {
		if (!on() || mc.level == null) {
			if (!found.isEmpty() || signature != 0) {
				found = List.of();
				signature = 0;
				TextRules.setAuto(List.of());
			}
			return;
		}
		long now = signature(mc);
		int excluded = Config.get().protectExcluded.size();
		if (now == signature && excluded == excludedSize) return;
		signature = now;
		excludedSize = excluded;
		read(mc);
	}

	/** A cheap fingerprint of everything Auto reads, from the identities of the objects the server sent. */
	private static long signature(Minecraft mc) {
		long h = 1125899906842597L;
		h = 31 * h + (Modules.NAME_PROTECT_NUMBERS.get() ? 1 : 2) + (Modules.NAME_PROTECT_ADDRESS.get() ? 4 : 8) + (Modules.NAME_PROTECT_RANKS.get() ? 16 : 32);
		h = 31 * h + Modules.NAME_PROTECT_NAME.getOrDefault().hashCode();
		ServerData server = mc.getCurrentServer();
		h = 31 * h + (server == null ? 0 : server.ip.hashCode());
		Objective objective = ScoreboardWidget.sidebarObjective(mc);
		if (objective != null) {
			Scoreboard scoreboard = objective.getScoreboard();
			h = 31 * h + System.identityHashCode(objective.getDisplayName());
			for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
				h = 31 * h + entry.owner().hashCode();
				h = 31 * h + entry.value();
				h = 31 * h + System.identityHashCode(entry.display());
				PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
				if (team != null) {
					h = 31 * h + System.identityHashCode(team.getPlayerPrefix());
					h = 31 * h + System.identityHashCode(team.getPlayerSuffix());
				}
			}
		}
		PlayerTabOverlayAccessor tab = (PlayerTabOverlayAccessor) Compat.tabList();
		h = 31 * h + System.identityHashCode(tab.eventide$header());
		h = 31 * h + System.identityHashCode(tab.eventide$footer());
		ClientPacketListener connection = mc.getConnection();
		if (connection != null && Modules.NAME_PROTECT_RANKS.get()) {
			for (PlayerInfo info : connection.getOnlinePlayers()) {
				PlayerTeam team = info.getTeam();
				if (team != null) h = 31 * h + System.identityHashCode(team.getPlayerPrefix());
			}
		}
		return h == 0 ? 1 : h;
	}

	private static void read(Minecraft mc) {
		Map<String, Found> out = new LinkedHashMap<>();
		List<String> excluded = Config.get().protectExcluded;
		String own = mc.getUser().getName();
		add(out, excluded, Kind.NAME, "name:you", own, own, Modules.NAME_PROTECT_NAME.getOrDefault(), "Everywhere");
		ServerData server = mc.getCurrentServer();
		if (server != null && Modules.NAME_PROTECT_ADDRESS.get()) {
			String ip = server.ip.trim();
			int colon = ip.lastIndexOf(':');
			String host = colon > 0 && ip.indexOf(':') == colon ? ip.substring(0, colon) : ip;
			if (!host.isEmpty()) add(out, excluded, Kind.ADDRESS, "address:" + host.toLowerCase(Locale.ROOT), host, host, HIDDEN_ADDRESS, "The server you joined");
		}
		Objective objective = ScoreboardWidget.sidebarObjective(mc);
		if (objective != null) {
			Scoreboard scoreboard = objective.getScoreboard();
			lookAt(out, excluded, objective.getDisplayName().getString(), "Scoreboard", own);
			for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
				if (entry.isHidden()) continue;
				Component line = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName());
				lookAt(out, excluded, line.getString(), "Scoreboard", own);
			}
		}
		PlayerTabOverlayAccessor tab = (PlayerTabOverlayAccessor) Compat.tabList();
		for (Component part : new Component[] {tab.eventide$header(), tab.eventide$footer()}) {
			if (part == null) continue;
			for (String line : part.getString().split("\n")) lookAt(out, excluded, line, "Tab list", own);
		}
		ClientPacketListener connection = mc.getConnection();
		if (connection != null && Modules.NAME_PROTECT_RANKS.get()) {
			for (PlayerInfo info : connection.getOnlinePlayers()) {
				PlayerTeam team = info.getTeam();
				if (team != null) ranks(out, excluded, team.getPlayerPrefix().getString(), "Nametags and tab");
			}
		}
		List<Found> list = List.copyOf(out.values());
		List<TextRules.Rule> rules = new ArrayList<>();
		for (Found item : list) {
			if (item.excluded() || item.kind() == Kind.NAME) continue;
			rules.add(TextRules.compile(item.find(), item.replacement()));
		}
		found = list;
		TextRules.setAuto(rules);
	}

	private static void lookAt(Map<String, Found> out, List<String> excluded, String raw, String where, String own) {
		String line = stripCodes(raw).strip();
		if (line.isEmpty()) return;
		if (Modules.NAME_PROTECT_ADDRESS.get()) {
			Matcher domain = DOMAIN.matcher(line);
			while (domain.find()) {
				String address = domain.group();
				add(out, excluded, Kind.ADDRESS, "address:" + address.toLowerCase(Locale.ROOT), address, address, HIDDEN_ADDRESS, where);
			}
			Matcher ip = IP.matcher(line);
			while (ip.find()) add(out, excluded, Kind.ADDRESS, "address:" + ip.group(), ip.group(), ip.group(), HIDDEN_ADDRESS, where);
		}
		if (Modules.NAME_PROTECT_RANKS.get()) ranks(out, excluded, line, where);
		if (!Modules.NAME_PROTECT_NUMBERS.get() || line.contains(own)) return;
		Matcher labelled = LABEL_NUMBER.matcher(line);
		if (labelled.matches()) {
			String label = labelled.group(1).strip();
			if (label.indexOf('#') >= 0 || label.indexOf('*') >= 0 || label.length() < 2) return;
			String head = line.substring(0, labelled.start(3));
			String shown = line.substring(0, labelled.end(3));
			add(out, excluded, Kind.NUMBER, "number:" + label.toLowerCase(Locale.ROOT), shown, head + "#", head + HIDDEN_NUMBER, where);
			return;
		}
		Matcher counted = NUMBER_LABEL.matcher(line);
		if (counted.matches()) {
			String label = counted.group(2).strip();
			if (label.indexOf('#') >= 0 || label.indexOf('*') >= 0) return;
			add(out, excluded, Kind.NUMBER, "count:" + label.toLowerCase(Locale.ROOT), counted.group(1) + " " + label, "# " + label,
					HIDDEN_NUMBER + " " + label, where);
		}
	}

	private static void ranks(Map<String, Found> out, List<String> excluded, String raw, String where) {
		Matcher rank = RANK.matcher(stripCodes(raw));
		while (rank.find()) {
			String tag = rank.group();
			if (tag.length() < 3) continue;
			add(out, excluded, Kind.RANK, "rank:" + tag, tag, tag, HIDDEN_RANK, where);
		}
	}

	private static void add(Map<String, Found> out, List<String> excluded, Kind kind, String key, String shown, String find, String replacement,
			String where) {
		out.putIfAbsent(key, new Found(kind, key, shown, find, replacement, where, excluded.contains(key)));
	}

	private static String stripCodes(String text) {
		if (text.indexOf('§') < 0) return text;
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§') {
				i++;
				continue;
			}
			out.append(c);
		}
		return out.toString();
	}

	/** For the test: whether a line reads as a stat, and what Auto would show instead. */
	public static String preview(String line) {
		Matcher labelled = LABEL_NUMBER.matcher(line);
		if (labelled.matches()) return line.substring(0, labelled.start(3)) + HIDDEN_NUMBER;
		Matcher counted = NUMBER_LABEL.matcher(line);
		if (counted.matches()) return HIDDEN_NUMBER + " " + counted.group(2).strip();
		return null;
	}
}
