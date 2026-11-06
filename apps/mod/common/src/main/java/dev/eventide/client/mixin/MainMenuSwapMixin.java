package dev.eventide.client.mixin;

import dev.eventide.client.gui.MainMenuScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Main Menu: Minecraft's title screen is swapped for Eventide's wherever the game opens it. Screens open through
 * Minecraft.setScreen, or Gui.setScreen from 26.2.
 */
@Mixin({Minecraft.class, Gui.class})
public abstract class MainMenuSwapMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen eventide$mainMenu(Screen screen) {
		return MainMenuScreen.replace(screen);
	}
}
