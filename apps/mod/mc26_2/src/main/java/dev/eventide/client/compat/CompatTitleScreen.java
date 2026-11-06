package dev.eventide.client.compat;

import dev.eventide.client.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * A title screen that draws through Draw, for Eventide's main menu: it is a {@link TitleScreen} (so the game and
 * other mods still know they're on the main menu) but adds none of Minecraft's buttons and draws only Eventide's.
 * Minecraft 26.2 version.
 */
public abstract class CompatTitleScreen extends TitleScreen {
	protected CompatTitleScreen() {
		super(false);
	}

	protected abstract void draw(Draw d, int mouseX, int mouseY, float delta);

	protected abstract void drawBackdrop(Draw d);

	@Override
	protected void init() {
	}

	@Override
	public void tick() {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		draw(new DrawImpl(graphics), mouseX, mouseY, delta);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		drawBackdrop(new DrawImpl(graphics));
	}
}
