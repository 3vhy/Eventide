<script setup lang="ts">
import { defineMessages, useVIntl } from '@modrinth/ui'
import { computed } from 'vue'

import type { GameInstance } from '@/helpers/types'

import { hasEventideFeatures } from './eventide'

// Eventide: the keys for the in-game client, so players find the hub and mod menu
const props = defineProps<{
	instance: GameInstance | null
}>()

const { formatMessage } = useVIntl()

const messages = defineMessages({
	title: { id: 'app.Eventide.ingame.title', defaultMessage: 'In game' },
	hub: { id: 'app.Eventide.ingame.hub', defaultMessage: 'Hub, HUD editor and mod menu' },
	zoom: { id: 'app.Eventide.ingame.zoom', defaultMessage: 'Zoom (scroll to adjust)' },
	freelook: { id: 'app.Eventide.ingame.freelook', defaultMessage: 'Freelook' },
	built: {
		id: 'app.Eventide.ingame.built-in',
		defaultMessage: '30+ mods built in: HUD, PvP tools, crosshair, waypoints, profiles and more.',
	},
	unsupported: {
		id: 'app.Eventide.ingame.unsupported',
		defaultMessage:
			"This instance doesn't get the Eventide in-game client. Pick a Fabric 1.21.11, 26.1 or 26.2 instance, or set one up from the instance picker.",
	},
	rightShift: { id: 'app.Eventide.ingame.key.right-shift', defaultMessage: 'Right Shift' },
	leftAlt: { id: 'app.Eventide.ingame.key.left-alt', defaultMessage: 'Left Alt' },
})

const supported = computed(() => props.instance !== null && hasEventideFeatures(props.instance))

const keys = computed(() => [
	{ key: formatMessage(messages.rightShift), label: formatMessage(messages.hub) },
	{ key: 'C', label: formatMessage(messages.zoom) },
	{ key: formatMessage(messages.leftAlt), label: formatMessage(messages.freelook) },
])
</script>

<template>
	<section class="tvh-panel">
		<header class="tvh-panel__header">
			<h3>{{ formatMessage(messages.title) }}</h3>
		</header>
		<template v-if="supported">
			<ul class="tvh-keys">
				<li v-for="entry in keys" :key="entry.key" class="tvh-keys__row">
					<kbd class="tvh-key">{{ entry.key }}</kbd>
					<span class="tvh-keys__label">{{ entry.label }}</span>
				</li>
			</ul>
			<p class="tvh-panel__empty">{{ formatMessage(messages.built) }}</p>
		</template>
		<p v-else class="tvh-panel__empty">{{ formatMessage(messages.unsupported) }}</p>
	</section>
</template>

<style scoped lang="scss" src="./panel.scss"></style>

<style scoped lang="scss">
.tvh-keys {
	display: flex;
	flex-direction: column;
	gap: 0.45rem;
	margin: 0;
	padding: 0;
	list-style: none;
}

.tvh-keys__row {
	display: flex;
	align-items: center;
	gap: 0.6rem;
}

.tvh-key {
	flex-shrink: 0;
	min-width: 2rem;
	padding: 0.15rem 0.45rem 0.25rem;
	font-family: inherit;
	font-size: 0.75rem;
	font-weight: 700;
	text-align: center;
	color: var(--color-contrast);
	background: var(--eventide-card-bg);
	border: 1px solid var(--eventide-card-border);
	border-bottom-width: 3px;
	border-radius: 0.3rem;
}

.tvh-keys__label {
	font-size: 0.875rem;
	color: var(--color-base);
}
</style>
