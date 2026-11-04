package dev.eventide.client.hud;

import dev.eventide.client.music.Lyrics;
import dev.eventide.client.music.MusicBridge;
import dev.eventide.client.music.NowPlaying;
import dev.eventide.client.ui.Ease;
import java.util.List;

/**
 * Follows the song's lyrics as it plays: the line being sung, the one before it (still fading out), the
 * next one, and how far through the current line the singer is, for the karaoke fill. A long intro or
 * instrumental break shows a note (♪) instead of nothing, with the line coming up under it.
 */
final class LyricTracker {
	static final String NOTE = "♪";
	private static final long CHANGE_MS = 520;
	/** A gap without words at least this long gets the note. */
	private static final double NOTE_GAP = 2.5;

	private List<Lyrics.Line> lines = null;
	private String songKey = null;
	private int index = -2;
	private String current = "";
	private String previous = "";
	private String next = "";
	private boolean marker = false;
	private boolean synced = true;
	private long changedAt = 0;
	private double lineAt = 0;
	private double lineEnd = 0;
	private boolean any = false;

	/** Catches up with {@code song}; returns whether a new line (or a new song) started. */
	boolean update(NowPlaying song) {
		if (song == null) {
			boolean had = !current.isEmpty();
			reset();
			return had;
		}
		Lyrics.want(song);
		Lyrics.View view = Lyrics.view(song);
		List<Lyrics.Line> list = view.lines();
		any = !list.isEmpty();
		synced = view.synced();
		double at = MusicBridge.position(song) + 0.12;
		int found = Lyrics.index(list, at);
		if (list == lines && found == index && song.key().equals(songKey)) return false;
		int upcoming = nextWords(list, found);
		String text;
		boolean note = false;
		if (found < 0) {
			double gap = upcoming < 0 ? 0 : list.get(upcoming).at();
			note = any && gap >= NOTE_GAP;
			text = note ? NOTE : "";
		} else {
			text = list.get(found).text();
			if (text.isBlank()) {
				double gap = upcoming < 0 ? Double.MAX_VALUE : list.get(upcoming).at() - list.get(found).at();
				note = gap >= NOTE_GAP;
				text = note ? NOTE : "";
			}
		}
		lines = list;
		index = found;
		songKey = song.key();
		previous = marker ? "" : current;
		current = text;
		marker = note;
		lineAt = found < 0 ? 0 : list.get(found).at();
		lineEnd = found + 1 < list.size() ? list.get(found + 1).at() : lineAt + 4;
		next = upcoming < 0 ? "" : list.get(upcoming).text();
		changedAt = System.currentTimeMillis();
		return true;
	}

	/** The first line with words after {@code found}, or -1. */
	private static int nextWords(List<Lyrics.Line> list, int found) {
		for (int i = found + 1; i < list.size(); i++) {
			if (!list.get(i).text().isBlank()) return i;
		}
		return -1;
	}

	private void reset() {
		lines = null;
		songKey = null;
		index = -2;
		current = "";
		previous = "";
		next = "";
		marker = false;
		any = false;
	}

	/** Whether the song has lyrics at all. */
	boolean any() {
		return any;
	}

	/** Whether the lyrics have real timings; plain lyrics are spread over the song and only roughly in time. */
	boolean synced() {
		return synced;
	}

	/** Whether the current "line" is the note for an intro or instrumental break. */
	boolean marker() {
		return marker;
	}

	String current() {
		return current;
	}

	String previous() {
		return previous;
	}

	String next() {
		return next;
	}

	/** How far the change to the current line has gone, 0..1. */
	float change() {
		return change(1);
	}

	/** How far the change to the current line has got (0..1), playing {@code speed} times as fast as usual. */
	float change(float speed) {
		return Ease.OUT.apply((System.currentTimeMillis() - changedAt) * Math.max(0.1f, speed) / (double) CHANGE_MS);
	}

	/** How much of the current line has been sung, 0..1. */
	float sung(NowPlaying song) {
		if (song == null || marker || lineEnd <= lineAt) return 1;
		return (float) Math.max(0, Math.min(1, (MusicBridge.position(song) - lineAt) / Math.min(lineEnd - lineAt, 8)));
	}
}
