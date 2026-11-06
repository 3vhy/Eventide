<script setup lang="ts">
import { PlayIcon, PlusIcon, SpinnerIcon, StopCircleIcon } from '@modrinth/assets'
import { defineMessages, injectNotificationManager, useVIntl } from '@modrinth/ui'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, inject, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import preview from '@/assets/images/eventide/preview.webp'
import InstancePicker from '@/components/ui/home/InstancePicker.vue'
import LaunchOverlay from '@/components/ui/home/LaunchOverlay.vue'
import { EVENTIDE_VERSIONS } from '@/components/ui/home/eventide'
import { useAppEvent } from '@/composables/use-app-event'
import { useAppSettings } from '@/composables/use-app-settings.ts'
import { handleSevereError } from '@/composables/use-error.js'
import { useInstanceMods } from '@/composables/use-instance-mods'
import { useSelectedInstance } from '@/composables/use-selected-instance'
import { get_default_user, users } from '@/helpers/auth'
import { install_create_instance, installJobInstanceId } from '@/helpers/install'
import { kill, run } from '@/helpers/instance'
import { maxMemoryQueryOptions } from '@/helpers/jre.js'
import { get_all } from '@/helpers/process'
import { appSettingsQueryOptions } from '@/helpers/settings'
import { traceStartupStep } from '@/helpers/startup-debug'
import { useRootBreadcrumb } from '@/providers/breadcrumbs'
import { injectOnboardingChecklist } from '@/providers/onboarding-checklist'

// Eventide home: a big look at the client on the left, and on the right "Ready to launch"
// with the version, your in-game name, mods on and memory, over a white Launch game button.
// Launching shows a full-window screen with a progress bar until the game is up.
defineOptions({
	name: 'HomePage',
})

const { formatMessage } = useVIntl()
const { handleError } = injectNotificationManager()
const { hasCreatedInstance } = injectOnboardingChecklist()
const showCreationModal = inject<() => void>('showCreationModal')
const appSettings = useAppSettings()
const queryClient = useQueryClient()
const router = useRouter()

const messages = defineMessages({
	home: { id: 'app.navigation.home', defaultMessage: 'Home' },
	welcome: { id: 'app.Eventide.home.welcome', defaultMessage: 'Welcome to Eventide' },
	welcomeBack: { id: 'app.Eventide.home.welcome-back', defaultMessage: 'Welcome back,' },
	modsOn: { id: 'app.Eventide.home.stat.mods', defaultMessage: 'mods on' },
	instances: {
		id: 'app.Eventide.home.stat.instances',
		defaultMessage: '{count, plural, one {instance} other {instances}}',
	},
	ram: { id: 'app.Eventide.home.stat.ram', defaultMessage: 'GB RAM' },
	ramTooltip: {
		id: 'app.Eventide.home.stat.ram.tooltip',
		defaultMessage:
			'Your PC has {total} GB. Minecraft can use {allocated} GB (change it in Settings).',
	},
	play: { id: 'app.Eventide.home.launch', defaultMessage: 'Launch game' },
	eyebrow: { id: 'app.Eventide.home.eyebrow', defaultMessage: 'Eventide client' },
	ready: { id: 'app.Eventide.home.ready', defaultMessage: 'Ready to launch' },
	inGame: { id: 'app.Eventide.home.in-game', defaultMessage: 'In game' },
	setUp: { id: 'app.Eventide.home.set-up', defaultMessage: 'Set up Eventide' },
	lede: {
		id: 'app.Eventide.home.lede',
		defaultMessage:
			'A Minecraft client with 80+ built-in mods, a clean HUD, knives, skies and smooth performance. Built for 1.21.11 and 26.1.',
	},
	version: { id: 'app.Eventide.home.version', defaultMessage: 'Version' },
	nickname: { id: 'app.Eventide.home.nickname', defaultMessage: 'In-game nickname' },
	notSignedIn: { id: 'app.Eventide.home.not-signed-in', defaultMessage: 'Not signed in' },
	memory: { id: 'app.Eventide.home.memory', defaultMessage: 'Memory' },
	editInstance: { id: 'app.Eventide.home.edit-instance', defaultMessage: 'Edit instance and add mods' },
	stop: { id: 'app.Eventide.home.stop', defaultMessage: 'Stop' },
	installing: { id: 'app.Eventide.home.installing', defaultMessage: 'Installing' },
	createFirst: { id: 'app.Eventide.home.create-first', defaultMessage: 'Set up' },
})

useRootBreadcrumb({
	slot: 'root',
	id: 'home',
	label: formatMessage(messages.home),
	to: '/',
	visual: { type: 'icon', component: PlayIcon },
})

const { instances, selected, select, instancesQuery } = useSelectedInstance()
if (hasCreatedInstance.value) {
	await traceStartupStep('Load home instances', () => instancesQuery.suspense()).catch(handleError)
}

type MinecraftAccount = { profile?: { id: string; name: string } }
/** The signed-in Minecraft account's name, for the greeting. */
const playerQuery = useQuery({
	queryKey: ['eventide', 'player-name'],
	queryFn: async (): Promise<string | null> => {
		const id = await get_default_user()
		if (!id) return null
		const accounts = ((await users()) ?? []) as MinecraftAccount[]
		return accounts.find((account) => account.profile?.id === id)?.profile?.name ?? null
	},
})
const playerName = computed(() => playerQuery.data.value ?? null)

const selectedId = computed(() => selected.value?.id ?? null)
const { enabledCount } = useInstanceMods(selectedId)

const settingsQuery = useQuery(appSettingsQueryOptions())
// How much of it Minecraft is allowed to use (shown on hover)
const allocatedGb = computed(() => {
	const megabytes =
		selected.value?.memory?.maximum ?? settingsQuery.data.value?.memory?.maximum ?? null
	if (!megabytes) return null
	return Math.round((megabytes / 1024) * 10) / 10
})
// The PC's total memory (reported in KB)
const maxMemoryQuery = useQuery(maxMemoryQueryOptions())
const ramGb = computed(() => {
	const kilobytes = maxMemoryQuery.data.value
	if (!kilobytes) return null
	return Math.round(kilobytes / 1024 / 1024)
})

type ProcessMetadata = { uuid: string; instance_id: string }
const processesQuery = useQuery({
	queryKey: ['processes', 'list'],
	queryFn: (): Promise<ProcessMetadata[]> => get_all(),
})
useAppEvent('process', () => {
	void queryClient.invalidateQueries({ queryKey: ['processes', 'list'] })
})

const isRunning = computed(
	() =>
		!!selected.value &&
		(processesQuery.data.value ?? []).some((process) => process.instance_id === selected.value?.id),
)
const isInstalling = computed(
	() =>
		selected.value?.install_stage === 'minecraft_installing' ||
		selected.value?.install_stage === 'pack_installing',
)

const playLabel = computed(() => {
	if (isInstalling.value) return formatMessage(messages.installing)
	if (isRunning.value) return formatMessage(messages.stop)
	if (!selected.value) return formatMessage(messages.createFirst)
	return formatMessage(messages.play)
})

/** Makes a Fabric instance for a eventide-supported version; the Eventide mod is added on launch. */
async function createEventideInstance(gameVersion: string) {
	const job = await install_create_instance({
		name: `Eventide ${gameVersion}`,
		gameVersion,
		loader: 'fabric',
		loaderVersion: 'stable',
		iconPath: null,
	}).catch(handleError)
	const instanceId = job ? installJobInstanceId(job) : null
	if (instanceId) select(instanceId)
}

async function onPlay() {
	const instance = selected.value
	if (!instance) {
		await createEventideInstance(EVENTIDE_VERSIONS[0])
		return
	}
	if (isRunning.value) {
		await kill(instance.id).catch(handleError)
		return
	}
	if (isInstalling.value || instance.quarantined) return
	await run(instance.id).catch((error) => {
		launching.value = false
		handleSevereError(error, { instanceId: instance.id })
	})
}

/** The launching screen: up from Launch until the game is running (or cancelled). */
const launching = ref(false)
const launched = ref(false)

watch(isRunning, (running) => {
	if (!launching.value || !running) return
	launched.value = true
	setTimeout(() => {
		launching.value = false
		launched.value = false
	}, 700)
})

async function onLaunch() {
	if (selected.value && !isRunning.value && !isInstalling.value && !selected.value.quarantined) {
		launching.value = true
		launched.value = false
	}
	try {
		await onPlay()
	} catch {
		launching.value = false
	}
}

async function onCancelLaunch() {
	const instance = selected.value
	launching.value = false
	if (instance && isRunning.value) await kill(instance.id).catch(handleError)
}

function editInstance() {
	if (selected.value) router.push(`/instance/${encodeURIComponent(selected.value.id)}`)
}
</script>

<template>
	<div class="ev-home">
		<section class="ev-stage" aria-hidden="true">
			<img class="ev-stage__shot" :src="preview" alt="" />
			<div class="ev-stage__shade" />
			<div class="ev-stage__caption">
				<span class="ev-stage__live" />
				{{ selected?.name ?? 'Eventide' }}
			</div>
		</section>

		<section class="ev-launch">
			<p class="ev-launch__eyebrow">{{ formatMessage(messages.eyebrow) }}</p>
			<h1 class="ev-launch__title">
				{{
					isRunning
						? formatMessage(messages.inGame)
						: isInstalling
							? formatMessage(messages.installing)
							: selected
								? formatMessage(messages.ready)
								: formatMessage(messages.setUp)
				}}
			</h1>
			<p class="ev-launch__lede">{{ formatMessage(messages.lede) }}</p>

			<div class="ev-facts">
				<div class="ev-fact ev-fact--wide">
					<span class="ev-fact__label">{{ formatMessage(messages.version) }}</span>
					<InstancePicker
						class="ev-fact__picker"
						:instances="instances"
						:selected="selected"
						@select="select"
						@create="showCreationModal?.()"
						@create-eventide="createEventideInstance"
					/>
				</div>
				<div class="ev-fact">
					<span class="ev-fact__label">{{ formatMessage(messages.nickname) }}</span>
					<span class="ev-fact__value">{{ playerName ?? formatMessage(messages.notSignedIn) }}</span>
				</div>
				<div class="ev-fact">
					<span class="ev-fact__label">{{ formatMessage(messages.modsOn) }}</span>
					<span class="ev-fact__value">{{ selected ? enabledCount : '-' }}</span>
				</div>
				<div
					v-tooltip="
						ramGb && allocatedGb
							? formatMessage(messages.ramTooltip, { total: ramGb, allocated: allocatedGb })
							: undefined
					"
					class="ev-fact"
				>
					<span class="ev-fact__label">{{ formatMessage(messages.memory) }}</span>
					<span class="ev-fact__value">{{ allocatedGb ? `${allocatedGb} GB` : '-' }}</span>
				</div>
				<div class="ev-fact">
					<span class="ev-fact__label">{{ formatMessage(messages.instances, { count: instances.length }) }}</span>
					<span class="ev-fact__value">{{ instances.length }}</span>
				</div>
			</div>

			<div class="ev-launch__grow" />

			<button
				class="ev-launch__button"
				:class="{ 'ev-launch__button--stop': isRunning }"
				:disabled="isInstalling"
				@click="onLaunch"
			>
				<SpinnerIcon v-if="isInstalling" class="animate-spin" />
				<StopCircleIcon v-else-if="isRunning" />
				<PlusIcon v-else-if="!selected" />
				<PlayIcon v-else />
				<span>{{ playLabel }}</span>
			</button>
			<button v-if="selected" class="ev-launch__link" @click="editInstance">
				{{ formatMessage(messages.editInstance) }}
			</button>
		</section>

		<LaunchOverlay :open="launching" :done="launched" @cancel="onCancelLaunch" />
	</div>
</template>

<style scoped lang="scss">
.ev-home {
	display: grid;
	grid-template-columns: minmax(0, 1.12fr) minmax(20rem, 1fr);
	height: 100%;
	min-height: 32rem;
}

.ev-stage {
	position: relative;
	margin: 1rem 0 1rem 1rem;
	border-radius: 14px;
	overflow: hidden;
	background: #0f0f16;
	box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.06);
}

.ev-stage__shot {
	position: absolute;
	inset: 0;
	width: 100%;
	height: 100%;
	object-fit: cover;
	filter: saturate(1.05);
	transform: scale(1.02);
	animation: ev-drift 28s ease-in-out infinite alternate;
}

.ev-stage__shade {
	position: absolute;
	inset: 0;
	background:
		linear-gradient(90deg, transparent 55%, rgba(13, 13, 17, 0.9) 100%),
		linear-gradient(0deg, rgba(8, 8, 11, 0.75) 0%, transparent 35%);
}

.ev-stage__caption {
	position: absolute;
	left: 1.25rem;
	bottom: 1.1rem;
	display: inline-flex;
	align-items: center;
	gap: 0.5rem;
	font-size: 0.8rem;
	font-weight: 600;
	color: #d6d6df;
}

.ev-stage__live {
	width: 0.45rem;
	height: 0.45rem;
	border-radius: 50%;
	background: #8b7cf6;
	box-shadow: 0 0 0 3px rgba(139, 124, 246, 0.2);
}

.ev-launch {
	display: flex;
	flex-direction: column;
	padding: 2.75rem 2.25rem 1.75rem;
	min-width: 0;
}

.ev-launch__eyebrow {
	margin: 0 0 0.5rem;
	font-size: 0.7rem;
	font-weight: 600;
	letter-spacing: 0.22em;
	text-transform: uppercase;
	color: #7d7d8c;
}

.ev-launch__title {
	margin: 0 0 1.1rem;
	font-size: 2.1rem;
	line-height: 1.1;
	color: #ececf1;
}

.ev-launch__lede {
	margin: 0 0 1.75rem;
	padding-left: 0.9rem;
	border-left: 2px solid #26262e;
	max-width: 30rem;
	font-size: 0.9rem;
	line-height: 1.6;
	color: #8f8f9d;
}

.ev-facts {
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 0.6rem;
}

.ev-fact {
	display: flex;
	flex-direction: column;
	gap: 0.35rem;
	min-width: 0;
	padding: 0.8rem 0.95rem;
	border-radius: 12px;
	background: #121217;
	box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.06);
}

.ev-fact--wide {
	grid-column: 1 / -1;
}

.ev-fact__label {
	font-size: 0.65rem;
	font-weight: 600;
	letter-spacing: 0.16em;
	text-transform: uppercase;
	color: #6e6e7c;
}

.ev-fact__value {
	overflow: hidden;
	font-size: 1rem;
	font-weight: 600;
	color: #ececf1;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.ev-fact__picker {
	width: 100%;
}

.ev-launch__grow {
	flex: 1;
	min-height: 1.5rem;
}

.ev-launch__button {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	gap: 0.55rem;
	width: 100%;
	height: 3.4rem;
	border: none;
	border-radius: 12px;
	background: #e7e7ee;
	color: #0d0d11;
	font-size: 1rem;
	font-weight: 700;
	cursor: pointer;
	box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
	transition:
		background-color 0.15s ease-out,
		transform 0.12s ease-out;

	svg {
		width: 1.15rem;
		height: 1.15rem;
	}

	&:hover:not(:disabled) {
		background: #ffffff;
	}

	&:active:not(:disabled) {
		transform: scale(0.985);
	}

	&:disabled {
		opacity: 0.6;
		cursor: default;
	}
}

.ev-launch__button--stop {
	background: #2a1a1d;
	color: #f2777a;
	box-shadow: inset 0 0 0 1px rgba(229, 72, 77, 0.35);

	&:hover:not(:disabled) {
		background: #33202a;
	}
}

.ev-launch__link {
	align-self: center;
	margin-top: 0.85rem;
	border: none;
	background: none;
	color: #7d7d8c;
	font-size: 0.8rem;
	font-weight: 600;
	cursor: pointer;

	&:hover {
		color: #c9c1ff;
	}
}

@keyframes ev-drift {
	from {
		transform: scale(1.02) translate(0, 0);
	}
	to {
		transform: scale(1.08) translate(-1.5%, -1%);
	}
}

@media (max-width: 900px) {
	.ev-home {
		grid-template-columns: 1fr;
	}

	.ev-stage {
		min-height: 14rem;
		margin: 1rem 1rem 0;
	}
}

@media (prefers-reduced-motion: reduce) {
	.ev-stage__shot {
		animation: none;
	}
}
</style>
