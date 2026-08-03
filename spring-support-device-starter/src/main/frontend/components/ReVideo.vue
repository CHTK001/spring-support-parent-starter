<template>
  <div ref="containerRef" class="re-video relative w-full h-full bg-black rounded-xl overflow-hidden">
    <!-- 原生 video 元素，由 hls.js / flv.js 接管 source -->
    <video
      ref="videoRef"
      class="w-full h-full object-contain bg-black"
      :poster="poster"
      :muted="muted"
      :autoplay="autoplay"
      :controls="controls"
      :playsinline="true"
      @error="onVideoError"
      @loadedmetadata="onLoaded"
    ></video>

    <!-- 占位/错误覆盖 -->
    <div
      v-if="!url || error"
      class="absolute inset-0 flex flex-col items-center justify-center text-el-text-secondary gap-2 pointer-events-none"
    >
      <el-icon :size="32"><VideoCamera /></el-icon>
      <span class="text-xs">{{ error || '暂无视频' }}</span>
      <span v-if="protocol" class="text-[10px] opacity-70">协议: {{ protocol }}</span>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="absolute inset-0 flex items-center justify-center bg-black/30 pointer-events-none">
      <span class="text-xs text-white">加载中...</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { VideoCamera } from '@element-plus/icons-vue'
import Hls from 'hls.js'
import flvjs from 'flv.js'

/**
 * 组件属性
 */
const props = defineProps<{
  /**
   * 视频源 URL（HLS m3u8 / FLV / MP4 / WebSocket）
   */
  url: string
  /**
   * 协议类型：hls / flv / mp4 / ws
   * <p>为空时按 URL 后缀自动识别。</p>
   */
  protocol?: string
  /**
   * 通道号（用于日志区分）
   */
  channel?: number
  /**
   * 静音
   */
  muted?: boolean
  /**
   * 自动播放
   */
  autoplay?: boolean
  /**
   * 显示原生控制条
   */
  controls?: boolean
  /**
   * 封面图
   */
  poster?: string
}>()

/**
 * 事件
 */
const emit = defineEmits<{
  (e: 'error', err: any): void
  (e: 'loaded'): void
}>()

const videoRef = ref<HTMLVideoElement | null>(null)
const containerRef = ref<HTMLDivElement | null>(null)
const error = ref<string>('')
const loading = ref(false)

/**
 * HLS.js 实例
 */
let hls: Hls | null = null
/**
 * flv.js 实例
 */
let flvPlayer: any = null

/**
 * 自动识别协议
 */
function detectProtocol(url: string, hint?: string): string {
  if (hint) return hint.toLowerCase()
  const u = url.toLowerCase()
  if (u.endsWith('.m3u8') || u.includes('.m3u8?')) return 'hls'
  if (u.endsWith('.flv') || u.includes('.flv?')) return 'flv'
  if (u.endsWith('.mp4') || u.includes('.mp4?')) return 'mp4'
  if (u.startsWith('ws://') || u.startsWith('wss://')) return 'ws'
  return 'mp4'
}

/**
 * 清理当前播放器
 */
function destroy() {
  if (hls) {
    try {
      hls.destroy()
    } catch (e) {
      // ignore
    }
    hls = null
  }
  if (flvPlayer) {
    try {
      flvPlayer.destroy()
    } catch (e) {
      // ignore
    }
    flvPlayer = null
  }
  const v = videoRef.value
  if (v) {
    v.removeAttribute('src')
    try {
      v.load()
    } catch (e) {
      // ignore
    }
  }
}

/**
 * 处理 HLS 流
 */
function playHls(url: string) {
  const v = videoRef.value
  if (!v) return
  destroy()
  if (Hls.isSupported()) {
    hls = new Hls({ enableWorker: true, lowLatencyMode: true })
    hls.loadSource(url)
    hls.attachMedia(v)
    hls.on(Hls.Events.ERROR, (_event, data) => {
      if (data.fatal) {
        error.value = `HLS 错误: ${data.type} - ${data.details}`
        emit('error', data)
        if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
          hls?.startLoad()
        } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
          hls?.recoverMediaError()
        }
      }
    })
  } else if (v.canPlayType('application/vnd.apple.mpegurl')) {
    // Safari 原生支持 HLS
    v.src = url
  } else {
    error.value = '当前浏览器不支持 HLS 播放'
    emit('error', new Error(error.value))
  }
}

/**
 * 处理 FLV 流
 */
function playFlv(url: string) {
  const v = videoRef.value
  if (!v || !flvjs.isSupported()) {
    error.value = '当前浏览器不支持 FLV 播放'
    emit('error', new Error(error.value))
    return
  }
  destroy()
  flvPlayer = flvjs.createPlayer(
    { type: 'flv', url, isLive: true },
    { enableWorker: false, enableStashBuffer: false, stashInitialSize: 128 },
  )
  flvPlayer.attachMediaElement(v)
  flvPlayer.load()
  flvPlayer.play().catch((e: any) => emit('error', e))
  flvPlayer.on(flvjs.Events.ERROR, (errType: string, errDetail: string) => {
    error.value = `FLV 错误: ${errType} - ${errDetail}`
    emit('error', new Error(error.value))
  })
}

/**
 * 处理原生支持的视频（mp4 / WebSocket）
 */
function playNative(url: string) {
  const v = videoRef.value
  if (!v) return
  destroy()
  v.src = url
}

/**
 * 加载视频源
 */
function load() {
  error.value = ''
  loading.value = true
  if (!props.url) {
    loading.value = false
    return
  }
  try {
    const proto = detectProtocol(props.url, props.protocol)
    switch (proto) {
      case 'hls':
        playHls(props.url)
        break
      case 'flv':
        playFlv(props.url)
        break
      default:
        playNative(props.url)
    }
    if (props.autoplay) {
      videoRef.value?.play().catch((e: any) => {
        // 自动播放被浏览器策略阻止不算致命错误
        console.warn('[ReVideo] autoplay blocked:', e?.message)
      })
    }
  } catch (e: any) {
    error.value = e?.message || '视频加载失败'
    emit('error', e)
  } finally {
    loading.value = false
  }
}

function onVideoError(e: any) {
  error.value = '视频播放错误'
  emit('error', e)
}

function onLoaded() {
  emit('loaded')
}

watch(
  () => [props.url, props.protocol],
  () => load(),
)

onMounted(load)
onBeforeUnmount(destroy)
</script>

<style scoped>
.re-video :deep(video) {
  width: 100%;
  height: 100%;
}
</style>
