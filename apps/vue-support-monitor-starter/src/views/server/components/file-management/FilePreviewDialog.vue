<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="80%"
    :close-on-click-modal="false"
    :destroy-on-close="true"
    class="file-preview-dialog"
  >
    <div class="preview-content" v-loading="loading">
      <!-- 文件信息 -->
      <div class="file-info" v-if="fileInfo">
        <div class="info-item">
          <span class="label">文件名:</span>
          <span class="value">{{ fileInfo.name }}</span>
        </div>
        <div class="info-item">
          <span class="label">大小:</span>
          <span class="value">{{ formatFileSize(fileInfo.size) }}</span>
        </div>
        <div class="info-item">
          <span class="label">修改时间:</span>
          <span class="value">{{ formatTime(fileInfo.modifiedTime) }}</span>
        </div>
        <div class="info-item">
          <span class="label">权限:</span>
          <span class="value">{{ fileInfo.permissions || "-" }}</span>
        </div>
      </div>

      <!-- 文件内容 -->
      <div class="file-content">
        <!-- 文本文件编辑器 -->
        <div v-if="isTextFile" class="text-editor">
          <div class="editor-toolbar">
            <el-button-group>
              <el-button
                size="small"
                :type="editMode ? 'primary' : 'default'"
                @click="toggleEditMode"
              >
                <IconifyIconOnline icon="ri:edit-line" class="mr-1" />
                {{ editMode ? "预览模式" : "编辑模式" }}
              </el-button>
              <el-button
                size="small"
                :disabled="!editMode || !hasChanges"
                @click="saveFile"
              >
                <IconifyIconOnline icon="ri:save-line" class="mr-1" />
                保存
              </el-button>
              <el-button size="small" @click="downloadFile">
                <IconifyIconOnline icon="ri:download-line" class="mr-1" />
                下载
              </el-button>
            </el-button-group>
          </div>

          <!-- 代码编辑器 -->
          <div class="editor-container">
            <el-input
              v-if="editMode"
              v-model="fileContent"
              type="textarea"
              :rows="20"
              placeholder="文件内容"
              @input="handleContentChange"
            />
            <pre v-else class="preview-text">{{ fileContent }}</pre>
          </div>
        </div>

        <!-- 图片预览 -->
        <div v-else-if="isImageFile" class="image-preview">
          <el-image
            :src="imageUrl"
            fit="contain"
            style="max-width: 100%; max-height: 500px"
            :preview-src-list="[imageUrl]"
          />
        </div>

        <!-- 不支持预览的文件 -->
        <div v-else class="unsupported-preview">
          <div class="unsupported-content">
            <IconifyIconOnline icon="ri:file-line" class="unsupported-icon" />
            <p>此文件类型不支持预览</p>
            <el-button type="primary" @click="downloadFile">
              <IconifyIconOnline icon="ri:download-line" class="mr-1" />
              下载文件
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="closeDialog">关闭</el-button>
      <el-button
        v-if="isTextFile && editMode && hasChanges"
        type="primary"
        @click="saveFile"
      >
        保存更改
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { formatBytes } from "@pureadmin/utils";
import dayjs from "dayjs";
import type { FileInfo } from "@/api/file-management";
import { readFileContent, saveFileContent, downloadFile as downloadFileApi } from "@/api/file-management";

// Props
const props = defineProps<{
  visible: boolean;
  serverId: number;
  fileInfo: FileInfo | null;
}>();

// Emits
const emit = defineEmits<{
  "update:visible": [value: boolean];
  "file-updated": [];
}>();

// 响应式数据
const loading = ref(false);
const editMode = ref(false);
const fileContent = ref("");
const originalContent = ref("");
const hasChanges = ref(false);

// 计算属性
const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit("update:visible", value),
});

const dialogTitle = computed(() => {
  if (!props.fileInfo) return "文件预览";
  return `文件预览 - ${props.fileInfo.name}`;
});

const isTextFile = computed(() => {
  if (!props.fileInfo) return false;
  
  const ext = props.fileInfo.name.split(".").pop()?.toLowerCase();
  const textExts = [
    "txt", "md", "json", "xml", "html", "css", "js", "ts", "vue",
    "jsx", "tsx", "py", "java", "c", "cpp", "h", "hpp", "php",
    "rb", "go", "rs", "sh", "bat", "yml", "yaml", "ini", "conf",
    "log", "csv", "sql", "properties", "gitignore", "dockerfile"
  ];
  
  return textExts.includes(ext || "") || props.fileInfo.size < 1024 * 1024; // 小于1MB的文件尝试作为文本处理
});

const isImageFile = computed(() => {
  if (!props.fileInfo) return false;
  
  const ext = props.fileInfo.name.split(".").pop()?.toLowerCase();
  const imageExts = ["jpg", "jpeg", "png", "gif", "svg", "bmp", "webp"];
  
  return imageExts.includes(ext || "");
});

const imageUrl = computed(() => {
  if (!props.fileInfo || !isImageFile.value) return "";
  
  // 构建图片URL，这里需要根据实际的下载API来构建
  return `/api/v1/gen/file-management/download?serverId=${props.serverId}&filePath=${encodeURIComponent(props.fileInfo.path)}`;
});

/**
 * 监听文件信息变化
 */
watch(
  () => props.fileInfo,
  (newFileInfo) => {
    if (newFileInfo && props.visible) {
      loadFileContent();
    }
  },
  { immediate: true }
);

/**
 * 监听对话框显示状态
 */
watch(
  () => props.visible,
  (visible) => {
    if (visible && props.fileInfo) {
      loadFileContent();
    } else {
      resetState();
    }
  }
);

/**
 * 格式化文件大小
 */
const formatFileSize = (size: number) => {
  return formatBytes(size);
};

/**
 * 格式化时间
 */
const formatTime = (time: string) => {
  return dayjs(time).format("YYYY-MM-DD HH:mm:ss");
};

/**
 * 加载文件内容
 */
const loadFileContent = async () => {
  if (!props.fileInfo || !isTextFile.value) return;

  try {
    loading.value = true;
    const res = await readFileContent(props.serverId, props.fileInfo.path);

    if (res.code === "00000" && res.data !== undefined) {
      fileContent.value = res.data;
      originalContent.value = res.data;
      hasChanges.value = false;
    } else {
      ElMessage.error("读取文件内容失败");
    }
  } catch (error) {
    console.error("读取文件内容失败:", error);
    ElMessage.error("读取文件内容失败");
  } finally {
    loading.value = false;
  }
};

/**
 * 处理内容变化
 */
const handleContentChange = () => {
  hasChanges.value = fileContent.value !== originalContent.value;
};

/**
 * 切换编辑模式
 */
const toggleEditMode = async () => {
  if (editMode.value && hasChanges.value) {
    try {
      await ElMessageBox.confirm(
        "您有未保存的更改，确定要切换到预览模式吗？",
        "确认",
        {
          type: "warning",
          confirmButtonText: "确定",
          cancelButtonText: "取消",
        }
      );
      
      // 恢复原始内容
      fileContent.value = originalContent.value;
      hasChanges.value = false;
    } catch {
      return; // 用户取消
    }
  }
  
  editMode.value = !editMode.value;
};

/**
 * 保存文件
 */
const saveFile = async () => {
  if (!props.fileInfo || !hasChanges.value) return;

  try {
    loading.value = true;
    const res = await saveFileContent(
      props.serverId,
      props.fileInfo.path,
      fileContent.value
    );

    if (res.code === "00000" && res.data?.success) {
      originalContent.value = fileContent.value;
      hasChanges.value = false;
      ElMessage.success("文件保存成功");
      emit("file-updated");
    } else {
      ElMessage.error(res.data?.message || "文件保存失败");
    }
  } catch (error) {
    console.error("文件保存失败:", error);
    ElMessage.error("文件保存失败");
  } finally {
    loading.value = false;
  }
};

/**
 * 下载文件
 */
const downloadFile = async () => {
  if (!props.fileInfo) return;

  try {
    const res = await downloadFileApi(props.serverId, props.fileInfo.path);
    
    // 创建下载链接
    const url = window.URL.createObjectURL(new Blob([res.data]));
    const link = document.createElement("a");
    link.href = url;
    link.download = props.fileInfo.name;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    
    ElMessage.success("文件下载成功");
  } catch (error) {
    console.error("文件下载失败:", error);
    ElMessage.error("文件下载失败");
  }
};

/**
 * 关闭对话框
 */
const closeDialog = async () => {
  if (hasChanges.value) {
    try {
      await ElMessageBox.confirm(
        "您有未保存的更改，确定要关闭吗？",
        "确认",
        {
          type: "warning",
          confirmButtonText: "确定",
          cancelButtonText: "取消",
        }
      );
    } catch {
      return; // 用户取消
    }
  }
  
  dialogVisible.value = false;
};

/**
 * 重置状态
 */
const resetState = () => {
  editMode.value = false;
  fileContent.value = "";
  originalContent.value = "";
  hasChanges.value = false;
};
</script>

<style scoped>
.file-preview-dialog {
  --el-dialog-content-font-size: 14px;
}

.preview-content {
  max-height: 70vh;
  overflow: auto;
}

.file-info {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 12px;
  background: var(--el-fill-color-extra-light);
  border-radius: 6px;
  margin-bottom: 16px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.label {
  font-weight: 500;
  color: var(--el-text-color-secondary);
  min-width: 60px;
}

.value {
  color: var(--el-text-color-primary);
}

.file-content {
  min-height: 300px;
}

.text-editor {
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  overflow: hidden;
}

.editor-toolbar {
  padding: 8px 12px;
  background: var(--el-fill-color-extra-light);
  border-bottom: 1px solid var(--el-border-color-light);
}

.editor-container {
  position: relative;
}

.preview-text {
  padding: 16px;
  margin: 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 500px;
  overflow: auto;
}

.image-preview {
  text-align: center;
  padding: 20px;
}

.unsupported-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

.unsupported-content {
  text-align: center;
  color: var(--el-text-color-secondary);
}

.unsupported-icon {
  font-size: 48px;
  margin-bottom: 16px;
  color: var(--el-text-color-placeholder);
}

/* 深色模式适配 */
:deep(.el-dialog) {
  background: var(--el-bg-color);
}

:deep(.el-dialog__header) {
  background: var(--el-fill-color-extra-light);
  border-bottom: 1px solid var(--el-border-color-light);
}

:deep(.el-dialog__body) {
  padding: 20px;
}

:deep(.el-textarea__inner) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
}

/* 滚动条样式 */
.preview-content::-webkit-scrollbar,
.preview-text::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.preview-content::-webkit-scrollbar-thumb,
.preview-text::-webkit-scrollbar-thumb {
  background: var(--el-border-color-light);
  border-radius: 3px;
}

.preview-content::-webkit-scrollbar-thumb:hover,
.preview-text::-webkit-scrollbar-thumb:hover {
  background: var(--el-border-color);
}

.preview-content::-webkit-scrollbar-track,
.preview-text::-webkit-scrollbar-track {
  background: transparent;
}
</style>
