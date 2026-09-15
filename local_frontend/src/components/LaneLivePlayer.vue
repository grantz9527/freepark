<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { ApiError, openTranscodedStream } from '@/api/client'
import { canPlayTranscodedFmp4, pipeFmp4ToVideo } from '@/lib/fmp4LivePlayer'

const props = defineProps<{
  url: string | null
  active: boolean
}>()

const { t, locale } = useI18n()

type PlayKind = 'empty' | 'invalid' | 'unsupported' | 'mjpeg' | 'http' | 'hls' | 'transcode' | 'mse-unsupported'

const kind = ref<PlayKind>('empty')
const playError = ref('')
const connecting = ref(false)
const videoRef = ref<HTMLVideoElement | null>(null)
let abort: AbortController | null = null

function isGo2rtcStreamName(text: string): boolean {
  return /^[A-Za-z0-9][A-Za-z0-9_.-]{0,119}$/.test(text)
}

function friendlyPlayError(error: unknown): string {
  if (error instanceof ApiError) {
    const raw = error.message.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
    if (!raw || raw.length > 120 || /internal server error/i.test(raw)) {
      return t('booths.monitorPlayFailed')
    }
    return raw
  }
  return t('booths.monitorPlayFailed')
}

function classify(url: string): PlayKind {
  if (isGo2rtcStreamName(url)) {
    return canPlayTranscodedFmp4() ? 'transcode' : 'mse-unsupported'
  }
  const scheme = url.match(/^([a-z][a-z0-9+.-]*):\/\//i)?.[1]?.toLowerCase()
  if (!scheme) return 'invalid'
  if (scheme === 'rtsp' || scheme === 'rtsps' || scheme === 'rtmp' || scheme === 'rtmps') {
    return canPlayTranscodedFmp4() ? 'transcode' : 'mse-unsupported'
  }
  if (scheme !== 'http' && scheme !== 'https') {
    return 'unsupported'
  }
  let parsed: URL
  try {
    parsed = new URL(url)
  } catch {
    return 'invalid'
  }
  const haystack = `${parsed.pathname}${parsed.search}`.toLowerCase()
  if (parsed.pathname.toLowerCase().endsWith('.m3u8') || haystack.includes('.m3u8')) {
    const video = document.createElement('video')
    if (video.canPlayType('application/vnd.apple.mpegurl') !== '') return 'hls'
    return canPlayTranscodedFmp4() ? 'transcode' : 'mse-unsupported'
  }
  if (haystack.includes('mjpeg') || haystack.includes('mjpg')) {
    return 'mjpeg'
  }
  return 'http'
}

function stop(): void {
  abort?.abort()
  abort = null
  connecting.value = false
  const video = videoRef.value
  if (video) {
    video.pause()
    video.removeAttribute('src')
    video.load()
  }
}

async function startTranscode(url: string): Promise<void> {
  const controller = new AbortController()
  abort = controller
  connecting.value = true
  playError.value = ''
  try {
    await nextTick()
    const video = videoRef.value
    if (!video) {
      playError.value = t('booths.monitorPlayFailed')
      return
    }
    const body = await openTranscodedStream(url, locale.value, controller.signal)
    if (controller.signal.aborted) return
    connecting.value = false
    await pipeFmp4ToVideo(video, body, controller.signal)
  } catch (error) {
    if (controller.signal.aborted) return
    connecting.value = false
    playError.value = friendlyPlayError(error)
  } finally {
    if (abort === controller) {
      connecting.value = false
    }
  }
}

async function restart(): Promise<void> {
  stop()
  playError.value = ''
  const url = props.url?.trim() ?? ''
  if (!props.active || !url) {
    kind.value = 'empty'
    return
  }
  kind.value = classify(url)
  if (kind.value === 'transcode') {
    await startTranscode(url)
  }
}

watch(
  () => [props.active, props.url] as const,
  () => {
    void restart()
  },
  { immediate: true },
)

onBeforeUnmount(stop)
</script>

<template>
  <div class="live">
    <div v-if="kind === 'mjpeg' && !playError" class="live-frame">
      <img :src="url ?? undefined" alt="" />
    </div>
    <div
      v-else-if="(kind === 'transcode' || kind === 'hls' || kind === 'http') && !playError"
      class="live-frame"
    >
      <p v-if="connecting" class="live-connecting">{{ t('booths.monitorConnecting') }}</p>
      <video
        ref="videoRef"
        muted
        autoplay
        playsinline
        :src="kind === 'transcode' ? undefined : (url ?? undefined)"
      />
    </div>
    <div v-else class="live-frame live-frame-empty">
      <p class="live-msg">
        <template v-if="kind === 'empty'">{{ t('booths.noMonitor') }}</template>
        <template v-else-if="kind === 'invalid' || kind === 'unsupported'">
          {{ t('booths.monitorUnsupported') }}
        </template>
        <template v-else-if="kind === 'mse-unsupported'">
          {{ t('barriers.viewStreamMseUnsupported') }}
        </template>
        <template v-else>{{ playError || t('booths.monitorPlayFailed') }}</template>
      </p>
    </div>
  </div>
</template>

<style scoped>
.live {
  height: 100%;
}

.live-frame {
  height: 100%;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #111;
  border-radius: 6px;
  overflow: hidden;
}

.live-frame img,
.live-frame video {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #111;
}

.live-frame-empty {
  background: #f2f4f3;
}

.live-msg,
.live-connecting {
  margin: 0;
  padding: 0.8rem 1rem;
  text-align: center;
  font-size: 0.85rem;
}

.live-msg {
  color: var(--muted);
}

.live-connecting {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #e5e7eb;
  z-index: 1;
}
</style>

