import type { GameInstance } from '@/helpers/types'

/** Minecraft versions the in-game Eventide mod is built for, newest first. */
export const EVENTIDE_VERSIONS = ['26.2', '26.1.2', '1.21.11'] as const

/** Fabric instances on these versions get the Eventide mod (HUD, PvP tools, Discord) on launch. */
export function hasEventideFeatures(
	instance: Pick<GameInstance, 'loader' | 'game_version'>,
): boolean {
	if (instance.loader !== 'fabric') return false
	return (
		instance.game_version === '1.21.11' ||
		instance.game_version === '26.1' ||
		instance.game_version === '26.1.1' ||
		instance.game_version === '26.1.2' ||
		instance.game_version === '26.2'
	)
}
