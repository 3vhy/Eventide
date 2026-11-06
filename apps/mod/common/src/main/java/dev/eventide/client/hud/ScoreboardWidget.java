package dev.eventide.client.hud;

import dev.eventide.client.compat.Compat;
import dev.eventide.client.Draw;
import dev.eventide.client.ui.Paint;
import dev.eventide.client.Hooks;
import dev.eventide.client.gui.HudEditorScreen;
import dev.eventide.client.module.Modules;
import dev.eventide.client.module.TextRules;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * The server's sidebar scoreboard as a HUD widget. Eventide draws it the way Minecraft does (same square
 * box, lines, order and layout), so it can also be moved, resized, given another background colour or
 * darkness, have its red numbers hidden, or be hidden altogether.
 */
public final class ScoreboardWidget extends HudWidget {
	private static final int SAMPLE_W = 96;
	private static final int LINE = 9;
	private static final int TITLE = 10;
	private static final int MAX_LINES = 15;
	/** Minecraft's sidebar order: highest score first, then by name. */
	private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparingInt(PlayerScoreEntry::value).reversed()
			.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
	private static final String[] SAMPLE = {"Kills: 29", "Deaths: 17", "Online: 272", "", "your.server"};

	private record Line(Component name, Component score, int scoreWidth) {
	}

	private record Board(Component title, List<Line> lines, int contentWidth) {
		int width() {
			return contentWidth + 4;
		}

		int height() {
			return lines.size() * LINE + TITLE;
		}
	}

	ScoreboardWidget() {
		super("scoreboard", Modules.SCOREBOARD, Anchor.MIDDLE_RIGHT);
	}

	/** The objective Minecraft shows in the sidebar for this player, if any. */
	public static Objective sidebarObjective(Minecraft mc) {
		if (mc.level == null || mc.player == null) return null;
		Scoreboard scoreboard = mc.level.getScoreboard();
		Objective objective = null;
		PlayerTeam team = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
		if (team != null) {
			DisplaySlot slot = Compat.teamSidebar(team);
			if (slot != null) objective = scoreboard.getDisplayObjective(slot);
		}
		return objective != null ? objective : scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
	}

	private static Board cached;
	private static Objective cachedFor;
	private static long cachedTick = Long.MIN_VALUE;
	private static int cachedRules = -1;
	private static boolean cachedHide;

	/**
	 * The lines to show, worked out like Minecraft does; null when there's no sidebar. Worked out once a game
	 * tick (the scoreboard only changes when the server says so), not every time the HUD asks.
	 */
	private static Board board(Minecraft mc) {
		Objective objective = sidebarObjective(mc);
		if (objective == null) return null;
		long tick = mc.level.getGameTime();
		boolean hide = Modules.SCOREBOARD_HIDE_NUMBERS.get();
		if (cached != null && objective == cachedFor && tick == cachedTick && TextRules.version() == cachedRules && hide == cachedHide) return cached;
		cached = build(mc, objective);
		cachedFor = objective;
		cachedTick = tick;
		cachedRules = TextRules.version();
		cachedHide = hide;
		return cached;
	}

	private static Board build(Minecraft mc, Objective objective) {
		Scoreboard scoreboard = objective.getScoreboard();
		NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
		boolean hideNumbers = Modules.SCOREBOARD_HIDE_NUMBERS.get();
		List<Line> lines = scoreboard.listPlayerScores(objective).stream()
				.filter(entry -> !entry.isHidden())
				.sorted(ORDER)
				.limit(MAX_LINES)
				.map(entry -> {
					Component name = TextRules.apply(
							PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()));
					Component score = hideNumbers ? Component.empty() : TextRules.apply(entry.formatValue(format));
					return new Line(name, score, hideNumbers ? 0 : mc.font.width(score));
				})
				.toList();
		Component title = TextRules.apply(objective.getDisplayName());
		int width = mc.font.width(title);
		int colon = mc.font.width(": ");
		for (Line line : lines) {
			width = Math.max(width, mc.font.width(line.name()) + (line.scoreWidth() > 0 ? colon + line.scoreWidth() : 0));
		}
		return new Board(title, lines, width);
	}

	@Override
	public boolean scaled() {
		return false;
	}

	@Override
	public boolean visible(Minecraft mc) {
		return sidebarObjective(mc) != null;
	}

	@Override
	public int width(Minecraft mc) {
		Board board = board(mc);
		return board == null ? SAMPLE_W : board.width();
	}

	@Override
	public int height(Minecraft mc) {
		Board board = board(mc);
		return board == null ? SAMPLE.length * LINE + TITLE : board.height();
	}

	/** Where Minecraft puts it: against the right edge, a little below the middle. */
	@Override
	protected int[] defaultPosition(Minecraft mc, int screenWidth, int screenHeight) {
		Board board = board(mc);
		int lines = board == null ? SAMPLE.length : board.lines().size();
		int bottom = screenHeight / 2 + lines * LINE / 3;
		return new int[] {screenWidth - width(mc) - 1, bottom - lines * LINE - TITLE};
	}

	/** Called in place of Minecraft's own scoreboard, so it stays in the same layer of the HUD. */
	public void drawInPlace(Draw d) {
		Minecraft mc = Minecraft.getInstance();
		if (!module.enabled() || Compat.screen() instanceof HudEditorScreen) return;
		Board board = board(mc);
		if (board == null) return;
		Hooks.scoreboardRan = true;
		float scale = HudRenderer.scaleFor(this);
		int[] pos = position(mc, Math.round(d.width() / scale), Math.round(d.height() / scale));
		d.push();
		d.scale(scale);
		drawBoard(d, mc, board, pos[0], pos[1]);
		d.pop();
	}

	@Override
	public void draw(Draw d, int x, int y, Minecraft mc, boolean preview) {
		if (!preview) return;
		Board board = board(mc);
		if (board != null) {
			drawBoard(d, mc, board, x, y);
			return;
		}
		// A sample, so the scoreboard can be placed and styled before joining a server that has one
		int h = SAMPLE.length * LINE + TITLE;
		background(d, x, y, SAMPLE_W, h);
		String title = "Scoreboard";
		d.text(title, x + (SAMPLE_W - mc.font.width(title)) / 2, y + 1, dev.eventide.client.ui.Tokens.TEXT, false);
		for (int i = 0; i < SAMPLE.length; i++) d.text(SAMPLE[i], x + 2, y + TITLE + i * LINE, dev.eventide.client.ui.Tokens.TEXT_2, false);
	}

	private void drawBoard(Draw d, Minecraft mc, Board board, int x, int y) {
		int w = board.width();
		background(d, x, y, w, board.height());
		int titleWidth = mc.font.width(board.title());
		d.text(board.title(), x + 2 + board.contentWidth() / 2 - titleWidth / 2, y + 1, 0xFFFFFFFF, false);
		int lineY = y + TITLE;
		for (Line line : board.lines()) {
			d.text(line.name(), x + 2, lineY, 0xFFFFFFFF, false);
			if (line.scoreWidth() > 0) d.text(line.score(), x + w - line.scoreWidth(), lineY, 0xFFFFFFFF, false);
			lineY += LINE;
		}
	}

	/** The box behind it, in the chosen colour and darkness (soft corners with Rounded HUD), with a slightly darker title strip. */
	private static void background(Draw d, int x, int y, int w, int h) {
		int alpha = (int) Math.round(Modules.SCOREBOARD_BACKGROUND.get() / 100.0 * 255);
		if (alpha <= 0) return;
		int rgb = Modules.SCOREBOARD_COLOR.get() & 0xFFFFFF;
		float r = Modules.THEME_ROUNDED_HUD.get() ? 4 : 0;
		Paint.rect(d, x, y, x + w, y + h, r, (alpha << 24) | rgb);
		if (Modules.SCOREBOARD_TITLE_BAR.get()) {
			int strip = (Math.min(255, Math.max(26, alpha / 3)) << 24) | rgb;
			Paint.rect(d, x, y, x + w, y + TITLE - 1, r, strip);
			if (r > 0) Paint.rect(d, x, y + TITLE - 1 - r, x + w, y + TITLE - 1, 0, strip);
		}
		if (r > 0) Paint.stroke(d, x, y, x + w, y + h, r, Hud.hairline(d), Paint.fade(0x1FFFFFFF, alpha / 255f));
	}
}
