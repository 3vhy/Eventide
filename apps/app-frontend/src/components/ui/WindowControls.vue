<template>
	<Teleport to="body">
		<section
			v-if="showControls"
			ref="controlsEl"
			class="ev-window-dots pointer-events-auto fixed top-0 right-0 z-[10001] flex h-11 items-center gap-2.5 px-4"
			data-tauri-drag-region-exclude
		>
			<button
				type="button"
				class="ev-dot ev-dot--minimize"
				aria-label="Minimize window"
				@click="getCurrentWindow().minimize()"
			/>
			<button
				type="button"
				class="ev-dot ev-dot--close"
				aria-label="Close window"
				@click="handleClose"
			/>
		</section>
	</Teleport>
</template>

<script setup>
import { getCurrentWindow } from '@tauri-apps/api/window'
import { saveWindowState, StateFlags } from '@tauri-apps/plugin-window-state'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

import { useAppSettings } from '@/composables/use-app-settings.ts'
import { get as getSettings } from '@/helpers/settings.ts'
import { getOS } from '@/helpers/utils.js'

const appSettings = useAppSettings()

const nativeDecorations = ref(true)
const isMaximized = ref(false)
const os = ref('')
const controlsEl = ref(null)

const alwaysShowAppControls = computed(() => appSettings.getFeatureFlag('always_show_app_controls'))

const showControls = computed(
	() =>
		alwaysShowAppControls.value ||
		(!nativeDecorations.value && (os.value === 'Windows' || os.value === 'Linux')),
)

function setWindowControlsWidth(width) {
	document.documentElement.style.setProperty('--window-controls-width', `${width}px`)
}

let resizeObserver
watch(controlsEl, (el) => {
	resizeObserver?.disconnect()
	resizeObserver = undefined
	if (!el) {
		setWindowControlsWidth(0)
		return
	}

	resizeObserver = new ResizeObserver(() => {
		setWindowControlsWidth(el.getBoundingClientRect().width)
	})
	resizeObserver.observe(el)
	setWindowControlsWidth(el.getBoundingClientRect().width)
})

onMounted(async () => {
	os.value = await getOS()

	const settings = await getSettings()
	nativeDecorations.value = settings.native_decorations

	if (os.value !== 'MacOS') {
		await getCurrentWindow().setDecorations(nativeDecorations.value)
	}

	isMaximized.value = await getCurrentWindow().isMaximized()

	const unlisten = await getCurrentWindow().onResized(async () => {
		isMaximized.value = await getCurrentWindow().isMaximized()
	})

	onUnmounted(() => {
		unlisten()
	})
})

onUnmounted(() => {
	resizeObserver?.disconnect()
	document.documentElement.style.removeProperty('--window-controls-width')
})

const handleClose = async () => {
	await saveWindowState(StateFlags.ALL)
	await getCurrentWindow().close()
}
</script>
<style lang="scss" scoped>
.ev-dot {
	position: relative;
	width: 13px;
	height: 13px;
	padding: 0;
	border: none;
	border-radius: 50%;
	cursor: pointer;
	transition:
		filter 0.12s ease-out,
		transform 0.12s ease-out;

	&::before {
		content: '';
		position: absolute;
		inset: -8px -5px;
	}

	&:hover {
		filter: brightness(1.15);
		transform: scale(1.08);
	}

	&:active {
		transform: scale(0.94);
	}

	&:focus-visible {
		outline: 2px solid rgba(255, 255, 255, 0.6);
		outline-offset: 2px;
	}
}

.ev-dot--minimize {
	background: #e8b13a;
}

.ev-dot--close {
	background: #e5484d;
}
</style>
