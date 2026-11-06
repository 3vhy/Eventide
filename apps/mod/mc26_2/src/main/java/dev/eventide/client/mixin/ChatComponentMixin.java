package dev.eventide.client.mixin;

import dev.eventide.client.module.ChatTweaks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Chat: timestamps, stacked repeats and a longer history. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements ChatTweaks.ShownLines {
	@Shadow @Final private List<GuiMessage> allMessages;
	@Shadow @Final private List<GuiMessage.Line> trimmedMessages;

	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Component eventide$decorate(Component message) {
		return ChatTweaks.decorate(message, () -> {
			if (!allMessages.isEmpty() && ChatTweaks.isPrevious(allMessages.get(0).content())) {
				allMessages.remove(0);
				ChatTweaks.removeNewestLines(trimmedMessages, GuiMessage.Line::endOfEntry);
			}
		});
	}

	@Override
	public List<String> eventide$shownLines() {
		List<String> lines = new ArrayList<>();
		for (GuiMessage.Line line : trimmedMessages) lines.add(ChatTweaks.plain(line.content()));
		return lines;
	}

	@ModifyConstant(method = {"addMessageToQueue", "addMessageToDisplayQueue"}, constant = @Constant(intValue = 100))
	private int eventide$history(int vanilla) {
		return ChatTweaks.historyLimit(vanilla);
	}
}
