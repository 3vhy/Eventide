<script setup lang="ts">
import { CheckIcon, ChevronDownIcon, CompassIcon, PlusIcon } from '@modrinth/assets'
import { Avatar, defineMessages, useVIntl } from '@modrinth/ui'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getInstanceIconUrl } from '@/helpers/instance'
import type { GameInstance } from '@/helpers/types'

import { loaderLabel } from './format'
import { hasEventideFeatures, EVENTIDE_VERSIONS } from './eventide'

// Eventide: the "VERSION" box next to Play. Each entry is an instance
// (a Minecraft version + loader + its mods), most recently played first.
const props = defineProps<{
	instances: GameInstance[]
	selected: GameInstance | null
}>()

const emit = defineEmits<{
	select: [id: string]
	create: []
	createEventide: [gameVersion: string]
}>()

/** Eventide versions the player doesn't have an instance for yet. */
const missingEventideVersions = computed(() =>
	EVENTIDE_VERSIONS.filter(
		(version) =>
			!props.instances.some(
				(instance) => instance.loader === 'fabric' && instance.game_version === version,
			),
	),
)

const { formatMessage } = useVIntl()
const router = useRouter()

const messages = defineMessages({
	label: { id: 'app.Eventide.picker.label', defaultMessage: 'Instance' },
	none: { id: 'app.Eventide.picker.none', defaultMessage: 'No instances yet' },
	newInstance: { id: 'app.Eventide.picker.new', defaultMessage: 'New instance' },
	browseModpacks: {
		id: 'app.Eventide.picker.browse-modpacks',
		defaultMessage: 'Get a modpack from Modrinth',
	},
	setUpEventide: {
		id: 'app.Eventide.picker.set-up',
		defaultMessage: 'Set up Eventide {version}',
	},
	eventideBadge: { id: 'app.Eventide.picker.badge', defaultMessage: 'Eventide' },
	eventideBadgeTooltip: {
		id: 'app.Eventide.picker.badge.tooltip',
		defaultMessage: 'Launches with the Eventide HUD, PvP tools and Discord presence',
	},
})

const open = ref(false)
const root = ref<HTMLElement>()

function onDocumentPointer(event: PointerEvent) {
	if (open.value && root.value && !root.value.contains(event.target as Node)) {
		open.value = false
	}
}

function onKey(event: KeyboardEvent) {
	if (event.key === 'Escape') open.value = false
}

onMounted(() => {
	document.addEventListener('pointerdown', onDocumentPointer)
	document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
	document.removeEventListener('pointerdown', onDocumentPointer)
	document.removeEventListener('keydown', onKey)
})

function choose(id: string) {
	emit('select', id)
	open.value = false
}

function create() {
	open.value = false
	emit('create')
}

function createEventide(version: string) {
	open.value = false
	emit('createEventide', version)
}

function browseModpacks() {
	open.value = false
	router.push('/browse/modpack')
}

function subtitle(instance: GameInstance) {
	return `${loaderLabel(instance.loader)} ${instance.game_version}`
}
</script>

<template>
	<div ref="root" class="tvh-picker" :class="{ 'tvh-picker--open': open }">
		<button
			class="tvh-picker__button"
			type="button"
			:aria-expanded="open"
			aria-haspopup="listbox"
			@click="open = !open"
		>
			<span class="tvh-picker__text">
				<span class="tvh-picker__label">{{ formatMessage(messages.label) }}</span>
				<span class="tvh-picker__value">
					{{ selected ? selected.name : formatMessage(messages.none) }}
				</span>
				<span v-if="selected" class="tvh-picker__sub">{{ subtitle(selected) }}</span>
			</span>
			<ChevronDownIcon class="tvh-picker__chevron" />
		</button>

		<Transition name="tvh-pop">
			<div v-if="open" class="tvh-picker__menu" role="listbox">
				<div class="tvh-picker__scroll">
					<button
						v-for="instance in instances"
						:key="instance.id"
						type="button"
						role="option"
						:aria-selected="instance.id === selected?.id"
						class="tvh-picker__option"
						@click="choose(instance.id)"
					>
						<Avatar
							:src="getInstanceIconUrl(instance.icon_path) ?? undefined"
							size="2rem"
							no-shadow
							class="!rounded-lg"
						/>
						<span class="tvh-picker__option-text">
							<span class="tvh-picker__option-name">
								{{ instance.name }}
								<span
									v-if="hasEventideFeatures(instance)"
									v-tooltip="formatMessage(messages.eventideBadgeTooltip)"
									class="tvh-picker__badge"
								>
									{{ formatMessage(messages.eventideBadge) }}
								</span>
							</span>
							<span class="tvh-picker__option-sub">{{ subtitle(instance) }}</span>
						</span>
						<CheckIcon v-if="instance.id === selected?.id" class="tvh-picker__check" />
					</button>
				</div>
				<div class="tvh-picker__actions">
					<button
						v-for="version in missingEventideVersions"
						:key="version"
						type="button"
						class="tvh-picker__action tvh-picker__action--eventide"
						@click="createEventide(version)"
					>
						<PlusIcon /> {{ formatMessage(messages.setUpEventide, { version }) }}
					</button>
					<button type="button" class="tvh-picker__action" @click="create">
						<PlusIcon /> {{ formatMessage(messages.newInstance) }}
					</button>
					<button type="button" class="tvh-picker__action" @click="browseModpacks">
						<CompassIcon /> {{ formatMessage(messages.browseModpacks) }}
					</button>
				</div>
			</div>
		</Transition>
	</div>
</template>

<style scoped lang="scss">
.tvh-picker {
	position: relative;
	min-width: 15rem;
}

.tvh-picker__button {
	display: flex;
	align-items: center;
	gap: 0.75rem;
	width: 100%;
	height: 100%;
	min-height: 3.6rem;
	padding: 0.55rem 0.95rem;
	border: none;
	border-radius: 10px;
	background: #17171d;
	box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.07);
	color: var(--color-contrast);
	text-align: left;
	cursor: pointer;
	transition:
		box-shadow 0.15s ease-out,
		background-color 0.15s ease-out;

	&:hover,
	&:focus-visible,
	.tvh-picker--open & {
		outline: none;
		background: #1b1b22;
		box-shadow: inset 0 0 0 1px rgba(139, 124, 246, 0.5);
	}
}

.tvh-picker__text {
	display: flex;
	flex-direction: column;
	min-width: 0;
	flex: 1;
}

.tvh-picker__label {
	font-size: 0.75rem;
	font-weight: 500;
	color: var(--color-secondary);
}

.tvh-picker__value {
	font-size: 1.1rem;
	font-weight: 800;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.tvh-picker__sub {
	font-size: 0.78rem;
	color: var(--color-secondary);
}

.tvh-picker__chevron {
	width: 1.1rem;
	height: 1.1rem;
	flex-shrink: 0;
	transition: transform 0.2s ease;

	.tvh-picker--open & {
		transform: rotate(180deg);
	}
}

.tvh-picker__menu {
	position: absolute;
	z-index: 20;
	left: 0;
	right: 0;
	top: calc(100% + 0.5rem);
	display: flex;
	flex-direction: column;
	padding: 0.4rem;
	border-radius: 0.5rem;
	border: 1px solid var(--eventide-card-border);
	background: var(--color-raised-bg);
	box-shadow: 0 18px 40px rgba(2, 8, 40, 0.5);
	min-width: 18rem;
}

.tvh-picker__scroll {
	display: flex;
	flex-direction: column;
	max-height: 16rem;
	overflow-y: auto;
}

.tvh-picker__option,
.tvh-picker__action {
	display: flex;
	align-items: center;
	gap: 0.65rem;
	width: 100%;
	padding: 0.5rem 0.6rem;
	border: none;
	border-radius: 0.7rem;
	background: transparent;
	color: var(--color-base);
	text-align: left;
	cursor: pointer;

	&:hover {
		background: var(--color-button-bg);
		color: var(--color-contrast);
	}
}

.tvh-picker__option[aria-selected='true'] {
	background: var(--color-brand-highlight);
	color: var(--color-contrast);
}

.tvh-picker__option-text {
	display: flex;
	flex-direction: column;
	min-width: 0;
	flex: 1;
}

.tvh-picker__option-name {
	font-weight: 700;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.tvh-picker__badge {
	display: inline-block;
	margin-left: 0.3rem;
	padding: 0 0.3rem;
	font-size: 0.65rem;
	font-weight: 700;
	line-height: 1.1rem;
	vertical-align: 0.1rem;
	color: #04123f;
	background: var(--eventide-cyan);
	clip-path: var(--eventide-pixel-corners);
}

.tvh-picker__action--eventide {
	color: var(--color-brand);
	font-weight: 700;
}

.tvh-picker__option-sub {
	font-size: 0.75rem;
	color: var(--color-secondary);
}

.tvh-picker__check {
	width: 1rem;
	height: 1rem;
	color: var(--color-brand);
}

.tvh-picker__actions {
	display: flex;
	flex-direction: column;
	margin-top: 0.3rem;
	padding-top: 0.3rem;
	border-top: 1px solid var(--color-divider);

	svg {
		width: 1rem;
		height: 1rem;
	}
}

.tvh-pop-enter-active,
.tvh-pop-leave-active {
	transition:
		opacity 0.15s ease,
		transform 0.15s ease;
}

.tvh-pop-enter-from,
.tvh-pop-leave-to {
	opacity: 0;
	transform: translateY(-6px);
}
</style>
