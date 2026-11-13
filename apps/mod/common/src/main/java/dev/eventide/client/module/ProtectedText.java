package dev.eventide.client.module;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSink;

/**
 * Text as Name Protect shows it, plus where each of its characters came from in the original.
 *
 * <p>Minecraft measures, cuts and wraps text by positions in the original string. A swapped name
 * that's longer or shorter than the real one moves every position after it, so whatever is told
 * about a character while protected text is read gets the original position instead
 * ({@link #wrap}). Positions handed out this way always stay inside the original string, never go
 * backwards and never start before where reading began.
 *
 * <p>Wrapping text into lines needs more than positions: a line break inside a swapped name would
 * cut the real name in half. So line wrapping works on the protected text itself (see
 * StringSplitterMixin), with protection paused while it reads that already protected text.
 */
public final class ProtectedText {
	private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);
	private static final ProtectedText UNCHANGED = new ProtectedText("", new int[0]);
	private static final int MEMO_LIMIT = 4096;

	/** The text to show. */
	public final String text;
	/** For each character of {@link #text}, its position in the original string. */
	private final int[] origin;

	private ProtectedText(String text, int[] origin) {
		this.text = text;
		this.origin = origin;
	}

	/** The position in the original string that character {@code index} of {@link #text} stands for. */
	public int origin(int index) {
		if (origin.length == 0) return 0;
		return origin[Math.max(0, Math.min(index, origin.length - 1))];
	}

	/** A sink that passes characters of {@link #text} on to {@code sink} with their original positions. */
	public FormattedCharSink wrap(FormattedCharSink sink) {
		return (index, style, codepoint) -> sink.accept(origin(index), style, codepoint);
	}

	/**
	 * The protected version of {@code original} from position {@code skip} on, or null when nothing
	 * there is protected (or protection is off).
	 */
	public static ProtectedText of(String original, int skip) {
		if (original == null || !NameProtect.active()) return null;
		if (skip != 0) return make(original, skip);
		State state = STATE.get();
		Object names = NameProtect.names();
		int rules = TextRules.version();
		if (state.memoNames != names || state.memoRules != rules || state.memo.size() > MEMO_LIMIT) {
			state.memo.clear();
			state.memoNames = names;
			state.memoRules = rules;
		}
		ProtectedText known = state.memo.get(original);
		if (known != null) return known == UNCHANGED ? null : known;
		ProtectedText made = make(original, 0);
		state.memo.put(original, made == null ? UNCHANGED : made);
		return made;
	}

	private static ProtectedText make(String original, int skip) {
		int start = Math.max(0, Math.min(skip, original.length()));
		String tail = start == 0 ? original : original.substring(start);
		Rewrite rewrite = TextRules.rewrite(NameProtect.rewrite(null, tail), tail);
		if (rewrite == null || rewrite.text.equals(tail)) return null;
		int[] origin = rewrite.origins(start);
		return origin == null ? null : new ProtectedText(rewrite.text, origin);
	}

	/**
	 * Called where Minecraft starts reading a string to draw or measure it. Returns what to read
	 * instead, or null to read {@code text} as it is: when nothing in it is protected, while
	 * protection is paused, and for the protected text this very method handed out a moment ago
	 * (which the caller reads by calling the same method again).
	 */
	public static ProtectedText forReading(String text, int skip) {
		if (text == null || !NameProtect.active()) return null;
		State state = STATE.get();
		if (state.handedOut == text) {
			state.handedOut = null;
			return null;
		}
		if (state.paused > 0) return null;
		ProtectedText swap = of(text, skip);
		if (swap != null) state.handedOut = swap.text;
		return swap;
	}

	/** Whether text about to be wrapped or cut should be protected first. */
	public static boolean shouldPrepare() {
		return NameProtect.active() && STATE.get().paused == 0;
	}

	/** Runs {@code action} with protection paused, for code that reads text that's already protected (or must not be). */
	public static <T> T paused(Supplier<T> action) {
		State state = STATE.get();
		state.paused++;
		try {
			return action.get();
		} finally {
			state.paused--;
		}
	}

	/** Runs {@code action} with protection on again, inside a {@link #paused} block. */
	public static void resumed(Runnable action) {
		State state = STATE.get();
		int was = state.paused;
		state.paused = 0;
		try {
			action.run();
		} finally {
			state.paused = was;
		}
	}

	/** The same text with every piece protected, keeping each piece's style; null when nothing changed. */
	public static FormattedText protect(FormattedText text, Style style) {
		if (text == null || !NameProtect.active()) return null;
		List<FormattedText> parts = new ArrayList<>();
		boolean[] changed = {false};
		text.visit((partStyle, part) -> {
			ProtectedText swap = of(part, 0);
			if (swap != null) changed[0] = true;
			parts.add(FormattedText.of(swap == null ? part : swap.text, partStyle));
			return Optional.empty();
		}, style);
		return changed[0] ? FormattedText.composite(parts) : null;
	}

	private static final class State {
		int paused;
		String handedOut;
		/**
		 * What {@link #of} made of each string lately ({@link #UNCHANGED} when nothing in it is protected). The HUD and menus draw the
		 * same strings every frame, so they're only searched again once the protected names or the rules change.
		 */
		final Map<String, ProtectedText> memo = new HashMap<>();
		Object memoNames;
		int memoRules;
	}

	/** Places to change in a text: start, end and what goes there. Filled in order, left to right. */
	static final class Edits {
		int size;
		int[] start = new int[4];
		int[] end = new int[4];
		String[] replacement = new String[4];

		void add(int from, int to, String with) {
			if (size == start.length) {
				start = java.util.Arrays.copyOf(start, size * 2);
				end = java.util.Arrays.copyOf(end, size * 2);
				replacement = java.util.Arrays.copyOf(replacement, size * 2);
			}
			start[size] = from;
			end[size] = to;
			replacement[size] = with;
			size++;
		}
	}

	/**
	 * Text changed step by step (names, then each text rule), remembering for each character the
	 * part of the original it stands for. Kept characters stand for themselves; characters of a swap
	 * all stand for the whole original part they replaced. A later change that cuts into an earlier
	 * swap takes that whole swap in, so swaps never overlap.
	 */
	static final class Rewrite {
		String text;
		private final int originalLength;
		private int[] from;
		private int[] to;
		private boolean[] kept;

		Rewrite(String original) {
			this.text = original;
			this.originalLength = original.length();
		}

		void apply(Edits edits) {
			if (edits == null || edits.size == 0) return;
			int n = text.length();
			if (kept == null) {
				from = new int[n];
				to = new int[n];
				kept = new boolean[n];
				for (int i = 0; i < n; i++) {
					from[i] = i;
					to[i] = i + 1;
					kept[i] = true;
				}
			}
			StringBuilder out = new StringBuilder(n + 16);
			int capacity = n + 16;
			int[] outFrom = new int[capacity];
			int[] outTo = new int[capacity];
			boolean[] outKept = new boolean[capacity];
			int size = 0;
			int i = 0;
			for (int e = 0; e < edits.size; e++) {
				int start = edits.start[e];
				int end = edits.end[e];
				if (start < i || end <= start || end > n) continue;
				while (start > i && sameSwap(start - 1, start)) start--;
				while (end < n && sameSwap(end - 1, end)) end++;
				String piece = text.substring(start, edits.start[e]) + edits.replacement[e] + text.substring(edits.end[e], end);
				int needed = size + (start - i) + piece.length();
				if (needed > capacity) {
					capacity = Math.max(capacity * 2, needed + 16);
					outFrom = java.util.Arrays.copyOf(outFrom, capacity);
					outTo = java.util.Arrays.copyOf(outTo, capacity);
					outKept = java.util.Arrays.copyOf(outKept, capacity);
				}
				for (int k = i; k < start; k++, size++) {
					out.append(text.charAt(k));
					outFrom[size] = from[k];
					outTo[size] = to[k];
					outKept[size] = kept[k];
				}
				int swapFrom = from[start];
				int swapTo = to[end - 1];
				for (int k = 0; k < piece.length(); k++, size++) {
					out.append(piece.charAt(k));
					outFrom[size] = swapFrom;
					outTo[size] = swapTo;
					outKept[size] = false;
				}
				i = end;
			}
			int needed = size + (n - i);
			if (needed > capacity) {
				capacity = needed;
				outFrom = java.util.Arrays.copyOf(outFrom, capacity);
				outTo = java.util.Arrays.copyOf(outTo, capacity);
				outKept = java.util.Arrays.copyOf(outKept, capacity);
			}
			for (int k = i; k < n; k++, size++) {
				out.append(text.charAt(k));
				outFrom[size] = from[k];
				outTo[size] = to[k];
				outKept[size] = kept[k];
			}
			text = out.toString();
			from = java.util.Arrays.copyOf(outFrom, size);
			to = java.util.Arrays.copyOf(outTo, size);
			kept = java.util.Arrays.copyOf(outKept, size);
		}

		private boolean sameSwap(int a, int b) {
			return !kept[a] && !kept[b] && from[a] == from[b] && to[a] == to[b];
		}

		/**
		 * Each character's position in the original, plus {@code offset}. Inside a swap, the last
		 * character takes the position that ends the original part, so "everything up to here"
		 * includes the whole real name; the others take the position just before the part, so text
		 * cut off inside a swap leaves the real name out entirely. Null in the one case that can't be
		 * mapped safely (a two-unit character standing for a one-character part at the very end).
		 */
		int[] origins(int offset) {
			int n = text.length();
			int[] result = new int[n];
			int j = 0;
			while (j < n) {
				if (kept[j]) {
					result[j] = from[j];
					j++;
					continue;
				}
				int g = j;
				while (g < n && sameSwap(j, g)) g++;
				int partStart = from[j];
				int partEnd = to[j];
				for (int k = j; k < g; k++) {
					int units = Character.charCount(text.codePointAt(k));
					result[k] = k + units >= g ? Math.max(partStart, partEnd - units) : Math.max(0, partStart - 1);
				}
				j = g;
			}
			int last = 0;
			for (int k = 0; k < n; k++) {
				last = Math.max(last, result[k]);
				result[k] = last;
				int units = Character.charCount(text.codePointAt(k));
				if (last + units > originalLength) return null;
			}
			if (offset != 0) {
				for (int k = 0; k < n; k++) result[k] += offset;
			}
			return result;
		}
	}
}
