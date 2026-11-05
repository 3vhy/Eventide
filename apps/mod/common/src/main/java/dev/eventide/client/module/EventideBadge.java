package dev.eventide.client.module;

import dev.eventide.client.EventideClient;
import dev.eventide.client.mixin.AvatarAccessor;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Eventide Badge: the Eventide logo before the name of everyone playing with Eventide, on their name tag and in
 * the tab list. There's no Eventide server keeping a list, so each Eventide client says so itself: it sets a bit
 * Minecraft leaves unused in the skin-layer settings every client already sends, which the server passes on to
 * the players around you. Any Eventide client nearby sees the bit and shows the badge; other clients ignore it.
 * Players seen with it are remembered for the session, so the tab list keeps their badge when they're out of
 * sight.
 */
public final class EventideBadge {
	/** The skin-layer bit Eventide sets: Minecraft's seven layers use the bits below it. */
	public static final int MARK = 0x80;
	private static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath(EventideClient.MOD_ID, "badge"));
	private static final Component GLYPH = Component.literal("").withStyle(Style.EMPTY.withFont(FONT));
	private static final Set<UUID> SEEN = new HashSet<>();
	private static boolean announced = false;

	private EventideBadge() {
	}

	/** Whether this client tells the players around it that it's Eventide. */
	public static boolean announces() {
		return Modules.BADGE.enabled() && Modules.BADGE_SHARE.get();
	}

	/** The skin-layer settings this client sends, with Eventide's bit set or cleared. */
	public static int mark(int customisation) {
		return announces() ? customisation | MARK : customisation & ~MARK;
	}

	/** Whether {@code entity} is a player using Eventide (you always are). */
	public static boolean uses(Entity entity) {
		Minecraft mc = Minecraft.getInstance();
		if (entity == mc.player) return true;
		if (!(entity instanceof Avatar avatar)) return false;
		return (avatar.getEntityData().get(AvatarAccessor.eventide$customisation()) & MARK) != 0;
	}

	/** Whether the badge goes on {@code entity}'s name tag. */
	public static boolean shows(Entity entity) {
		return Modules.BADGE.enabled() && uses(entity);
	}

	/** {@code name} with the badge in front of it. */
	public static Component badged(Component name) {
		return Component.empty().append(GLYPH).append(" ").append(name);
	}

	/** A tab list name, with the badge in front when that player is known to use Eventide. */
	public static Component tab(PlayerInfo info, Component name) {
		if (name == null || !Modules.BADGE.enabled() || !Modules.BADGE_TAB.get()) return name;
		Minecraft mc = Minecraft.getInstance();
		UUID id = info.getProfile().id();
		boolean you = mc.player != null && mc.player.getUUID().equals(id);
		return you || SEEN.contains(id) ? badged(name) : name;
	}

	/**
	 * Once a second: remembers who's using Eventide among the players in sight, and when the badge is switched
	 * on or off sends the server this client's settings again, so the players around see the change.
	 */
	public static void tick(Minecraft mc) {
		boolean now = announces();
		if (now != announced && mc.getConnection() != null) {
			mc.options.broadcastOptions();
			announced = now;
		}
		if (mc.level == null) {
			SEEN.clear();
			return;
		}
		if (mc.level.getGameTime() % 20 != 0) return;
		for (Player player : mc.level.players()) {
			if (uses(player)) SEEN.add(player.getUUID());
			else SEEN.remove(player.getUUID());
		}
	}
}
