<template>
  <div class="re-video w-full h-full bg-black flex items-center justify-center relative">
    <video
      ref="videoRef"
      class="w-full h-full object-contain"
      autoplay
      muted
      playsinline
      @error="onError"
    />
    <div v-if="error" class="absolute inset-0 flex items-center justify-center text-white">
      <el-empty description="视频加载失败" :image-size="80" />
    </div>
    <div v-else-if="!playing" class="absolute inset-0 flex items-center justify-center text-white">
      <el-icon :size="48"><VideoCamera /></el-icon>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { VideoCamera } from '@element-plus/icons-vue'

/**
 * 组件属性
 */
const props = defineProps<{
  /** 视频流地址 */
  url: string
  /** 通道号 */
  channel?: number
}>()

/**
 * 事件
 */
const emit = defineEmits<{
  (e: 'error', err: any): void
}>()

/**
 * video 元素引用
 */
const videoRef = ref<HTMLVideoElement>()

/**
 * 是否正在播放
 */
const playing = ref(false)

/**
 * 是否出错
 */
const error = ref(false)

/**
 * MediaSource 实例
 */
let mediaSource: MediaSource | null = null

/**
 * 关闭并清理资源
 */
function cleanup() {
  if (videoRef.value) {
    videoRef.value.pause()
    videoRef.value.removeAttribute('src')
    videoRef.value.load()
  }
  playing.value = false
  error.value = false
  mediaSource = null
}

/**
 * 加载视频流
 */
function loadStream() {
  cleanup()
  if (!props.url) return

  const video = videoRef.value
  if (!video) return

  try {
    // 实际场景可对接 MSE / WebSocket / HLS 等
    video.src = props.url
    video.play()
      .then(() => {
        playing.value = true
        error.value = false
      })
      .catch((err) => {
        error.value = true
        emit('error', err)
      })
  } catch (err) {
    error.value = true
    emit('error', err)
  }
}

/**
 * 错误处理
 */
function onError(e: Event) {
  error.value = true
  emit('error', e)
}

watch(() => props.url, loadStream)
watch(() => props.channel, loadStream)

onMounted(loadStream)
onUnmounted(cleanup)
</script>

<style scoped>
.re-video {
  background: #000;
}
</style>