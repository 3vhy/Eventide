<template>
	<Transition name="ev-launching">
		<div v-if="open" class="ev-launching" role="dialog" aria-modal="true" :aria-label="title">
			<div class="ev-launching__body">
				<EventideGlyph class="ev-launching__mark" />
				<h2 class="ev-launching__name">Eventide</h2>
				<p class="ev-launching__status">{{ status }}</p>
				<div class="ev-launching__bar">
					<span class="ev-launching__percent">{{ Math.round(shown) }}%</span>
					<div class="ev-launching__track">
						<div class="ev-launching__fill" :style="{ width: `${shown}%` }" />
					</div>
				</div>
				<button type="button" class="ev-launching__cancel" @click="emit('cancel')">
					<XCircleIcon aria-hidden="true" />
					Cancel
				</button>
			</div>
		</div>
	</Transition>
</template>

<script setup lang="ts">
import { XCircleIcon } from '@modrinth/assets'
import { onBeforeUnmount, ref, watch } from 'vue'

import EventideGlyph from '@/components/ui/brand/EventideGlyph.vue'
import { useAppEvent } from '@/composables/use-app-event'

/**
 * The launching screen: the Eventide mark, "Starting Eventide client...", a thin progress bar with its percent
 * and a Cancel button, over the whole launcher. Progress follows the launcher's own loading (downloads and
 * installing) and creeps on while the game starts, then fills once {@link done} turns true.
 */
const props = defineProps<{
	open: boolean
	done: boolean
	title?: string
}>()

const emit = defineEmits<{
	cancel: []
}>()

const shown = ref(0)
const status = ref('STARTING EVENTIDE CLIENT...')
let target = 0
let timer: ReturnType<typeof setInterval> | null = null

function tick() {
	if (!props.done) target = Math.min(92, Math.max(target, shown.value + (92 - shown.value) * 0.025))
	shown.value += (target - shown.value) * 0.18
}

watch(
	() => props.open,
	(open) => {
		if (timer) clearInterval(timer)
		timer = null
		if (!open) return
		shown.value = 0
		target = 4
		status.value = 'STARTING EVENTIDE CLIENT...'
		timer = setInterval(tick, 50)
	},
	{ immediate: true },
)

watch(
	() => props.done,
	(done) => {
		if (!done) return
		target = 100
		status.value = 'LAUNCHED'
	},
)

useAppEvent('loading', (e) => {
	if (!props.open || props.done || e.fraction == null) return
	target = Math.max(target, Math.min(90, e.fraction * 90))
	status.value = 'DOWNLOADING GAME FILES...'
})

onBeforeUnmount(() => {
	if (timer) clearInterval(timer)
})
</script>

<style scoped lang="scss">
.ev-launching {
	position: fixed;
	inset: 0;
	z-index: 400;
	display: flex;
	align-items: center;
	justify-content: center;
	background: rgba(8, 8, 11, 0.92);
	backdrop-filter: blur(6px);
}

.ev-launching__body {
	display: flex;
	flex-direction: column;
	align-items: center;
	width: min(32rem, 80vw);
}

.ev-launching__mark {
	width: 5.5rem;
	height: 5.5rem;
	margin-bottom: 1.1rem;
}

.ev-launching__name {
	margin: 0;
	font-size: 2rem;
	font-weight: 700;
	color: var(--color-text-primary, #ececf1);
}

.ev-launching__status {
	margin: 0.5rem 0 2.25rem;
	font-size: 0.72rem;
	font-weight: 600;
	letter-spacing: 0.22em;
	color: #8a8a99;
}

.ev-launching__bar {
	display: flex;
	flex-direction: column;
	gap: 0.5rem;
	width: 100%;
}

.ev-launching__percent {
	align-self: flex-end;
	font-size: 0.75rem;
	font-variant-numeric: tabular-nums;
	color: #b9b9c6;
}

.ev-launching__track {
	height: 4px;
	border-radius: 999px;
	background: #1d1d24;
	overflow: hidden;
}

.ev-launching__fill {
	height: 100%;
	border-radius: 999px;
	background: linear-gradient(90deg, #8b7cf6, #d7d1ff);
	transition: width 0.1s linear;
}

.ev-launching__cancel {
	display: inline-flex;
	align-items: center;
	gap: 0.45rem;
	margin-top: 2.5rem;
	padding: 0.55rem 1rem;
	border: 1px solid rgba(229, 72, 77, 0.35);
	border-radius: 10px;
	background: rgba(229, 72, 77, 0.1);
	color: #f2777a;
	font-size: 0.85rem;
	font-weight: 600;
	cursor: pointer;
	transition:
		background-color 0.12s ease-out,
		transform 0.12s ease-out;

	svg {
		width: 1rem;
		height: 1rem;
	}

	&:hover {
		background: rgba(229, 72, 77, 0.18);
	}

	&:active {
		transform: scale(0.97);
	}
}

.ev-launching-enter-active,
.ev-launching-leave-active {
	transition: opacity 0.22s ease-out;
}

.ev-launching-enter-from,
.ev-launching-leave-to {
	opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
	.ev-launching__fill {
		transition: none;
	}
}
</style>
