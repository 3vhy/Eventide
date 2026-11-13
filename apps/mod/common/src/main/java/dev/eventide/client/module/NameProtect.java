package dev.eventide.client.module;

import dev.eventide.client.Hooks;
import dev.eventide.client.compat.Compat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Name Protect: your username, and optionally everyone else's, is swapped for another name in all
 * text on screen (see {@link ProtectedText}). Only whole names are swapped, so "Steve" changes but
 * "Stevenson" doesn't. Other players get stable numbers for the session.
 */
public final class NameProtect {
	/** Name -> what to show instead. Replaced as a whole every tick, so drawing never sees it half built. */
	private static volatile Map<String, String> replacements = Map.of();
	private static volatile int shortest = Integer.MAX_VALUE;
	private static volatile int longest = 0;
	private static final Map<String, Integer> NUMBERS = new HashMap<>();
	private static ClientPacketListener numbered = null;

	private NameProtect() {
	}

	static void tick(Minecraft mc) {
		if (!Modules.NAME_PROTECT.enabled()) {
			if (!replacements.isEmpty()) set(Map.of());
			return;
		}
		Map<String, String> next = new HashMap<>();
		ClientPacketListener connection = mc.getConnection();
		if (connection != numbered) {
			numbered = connection;
			NUMBERS.clear();
		}
		String own = mc.getUser().getName();
		if (Modules.NAME_PROTECT_OTHERS.get() && connection != null) {
			List<String> names = new ArrayList<>();
			for (PlayerInfo info : connection.getOnlinePlayers()) {
				String name = Compat.profileName(info.getProfile());
				if (name != null && !name.equals(own)) names.add(name);
			}
			Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
			for (String name : names) {
				int number = NUMBERS.computeIfAbsent(name, key -> NUMBERS.size() + 1);
				next.put(name, "Player " + number);
			}
		}
		next.put(own, Modules.NAME_PROTECT_NAME.getOrDefault());
		set(next);
	}

	/** The protected names as they are now: a new object whenever they change, so text worked out from them can tell it's out of date. */
	static Object names() {
		return replacements;
	}

	private static void set(Map<String, String> next) {
		if (next.equals(replacements)) return;
		int min = Integer.MAX_VALUE;
		int max = 0;
		for (String name : next.keySet()) {
			min = Math.min(min, name.length());
			max = Math.max(max, name.length());
		}
		shortest = min;
		longest = max;
		replacements = Map.copyOf(next);
	}

	/** Whether Name Protect changes any text right now. */
	public static boolean active() {
		return !replacements.isEmpty() || TextRules.active();
	}

	/** The text with protected names swapped and the text rules applied, or the very same string when nothing matched. */
	public static String apply(String text) {
		ProtectedText swap = ProtectedText.of(text, 0);
		return swap == null ? text : swap.text;
	}

	/** Swaps protected names in the text so far ({@code original} when {@code rewrite} is still null). */
	static ProtectedText.Rewrite rewrite(ProtectedText.Rewrite rewrite, String original) {
		String text = rewrite == null ? original : rewrite.text;
		Map<String, String> map = replacements;
		if (map.isEmpty() || text.length() < shortest) return rewrite;
		int min = shortest;
		int max = longest;
		ProtectedText.Edits edits = null;
		int length = text.length();
		int i = 0;
		while (i < length) {
			char c = text.charAt(i);
			if (c == '§') {
				i += 2;
				continue;
			}
			if (!isNameChar(c)) {
				i++;
				continue;
			}
			int start = i;
			while (i < length && isNameChar(text.charAt(i))) i++;
			int size = i - start;
			if (size < min || size > max) continue;
			String name = text.substring(start, i);
			String replacement = map.get(name);
			if (replacement == null || replacement.equals(name)) continue;
			if (edits == null) edits = new ProtectedText.Edits();
			edits.add(start, i, replacement);
		}
		if (edits == null) return rewrite;
		Hooks.nameProtectRan = true;
		if (rewrite == null) rewrite = new ProtectedText.Rewrite(original);
		rewrite.apply(edits);
		return rewrite;
	}

	private static boolean isNameChar(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
	}
}
