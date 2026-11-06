<template>
	<Transition name="splash-fade" @after-leave="onAfterLeave">
		<div v-if="!doneLoading" class="splash-screen" data-tauri-drag-region>
			<!-- Eventide: the loading screen is the launching screen's look: the mark, the name and a thin bar -->
			<div class="splash-content" data-tauri-drag-region>
				<EventideGlyph class="splash-mark" />
				<span class="splash-name">Eventide</span>
				<span class="splash-status">{{ message ?? 'STARTING EVENTIDE...' }}</span>
				<div
					class="splash-track"
					role="progressbar"
					aria-valuemin="0"
					aria-valuemax="100"
					:aria-valuenow="Math.round(Math.min(loadingProgress, 100))"
				>
					<div class="splash-fill" :style="{ width: `${Math.min(loadingProgress, 100)}%` }" />
				</div>
			</div>
		</div>
	</Transition>
</template>

<script setup>
import { injectLoadingState } from '@modrinth/ui'
import { computed, onMounted, ref, watch } from 'vue'

import EventideGlyph from '@/components/ui/brand/EventideGlyph.vue'
import { useAppEvent } from '@/composables/use-app-event'
import { debugStartup } from '@/helpers/startup-debug'

const doneLoading = ref(false)
const loadingProgress = ref(0)
const message = ref()

const MIN_DISPLAY_MS = 500
const mountedAt = Date.now()

const loading = injectLoadingState()
onMounted(() => debugStartup('Splash mounted'))

function onAfterLeave() {
	debugStartup('Splash fade completed', { displayedMs: Date.now() - mountedAt })
	loading.setEnabled(true)
}

watch(
	[loading.barEnabled, loading.pending],
	([barEnabled, pending]) => {
		debugStartup('Splash loading state changed', { barEnabled, pending })
		if (barEnabled) {
			return
		}

		if (pending) {
			loadingProgress.value = 0
			fakeLoadingIncrease()
			return
		}

		const elapsed = Date.now() - mountedAt
		const delay = Math.max(0, MIN_DISPLAY_MS - elapsed)
		debugStartup('Splash dismissal scheduled', { delayMs: delay, displayedMs: elapsed })

		setTimeout(() => {
			if (loading.pending.value) {
				debugStartup('Splash dismissal deferred: new loading work')
				return
			}
			doneLoading.value = true
			debugStartup('Splash fade started', { displayedMs: Date.now() - mountedAt })
		}, delay)
	},
	{ immediate: true },
)

function fakeLoadingIncrease() {
	if (loadingProgress.value < 95) {
		setTimeout(() => {
			loadingProgress.value += 2
			fakeLoadingIncrease()
		}, 5)
	}
}

useAppEvent('loading', (e) => {
	if (e.event.type === 'directory_move') {
		loadingProgress.value = 100 * (e.fraction ?? 1)
		message.value = 'UPDATING APP FOLDER...'
	}
})
</script>

<style scoped lang="scss">
.splash-screen {
	position: fixed;
	inset: 0;
	z-index: 10000;
	display: flex;
	align-items: center;
	justify-content: center;
	overflow: hidden;
	background: #08080b;
}

.splash-content {
	display: flex;
	flex-direction: column;
	align-items: center;
	width: min(26rem, 70vw);
}

.splash-mark {
	width: 4.75rem;
	height: 4.75rem;
	margin-bottom: 1rem;
	animation: splash-rise 0.45s ease-out both;
}

.splash-name {
	font-size: 1.75rem;
	font-weight: 700;
	color: #ececf1;
	animation: splash-rise 0.45s 0.06s ease-out both;
}

.splash-status {
	margin: 0.45rem 0 1.9rem;
	font-size: 0.7rem;
	font-weight: 600;
	letter-spacing: 0.22em;
	color: #7d7d8c;
}

.splash-track {
	width: 100%;
	height: 4px;
	border-radius: 999px;
	background: #1d1d24;
	overflow: hidden;
}

.splash-fill {
	height: 100%;
	border-radius: 999px;
	background: linear-gradient(90deg, #8b7cf6, #d7d1ff);
	transition: width 0.15s linear;
}

.splash-fade-leave-active {
	transition: opacity 0.25s ease-out;
}

.splash-fade-leave-to {
	opacity: 0;
}

@keyframes splash-rise {
	from {
		opacity: 0;
		transform: translateY(6px);
	}
}

@media (prefers-reduced-motion: reduce) {
	.splash-mark,
	.splash-name {
		animation: none;
	}
}
</style>
