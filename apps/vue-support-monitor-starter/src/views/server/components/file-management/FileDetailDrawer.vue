<template>
  <el-drawer
    v-model="drawerVisible"
    :title="drawerTitle"
    direction="rtl"
    size="400px"
    :close-on-click-modal="false"
  >
    <div class="file-detail" v-if="fileInfo">
      <!-- 文件图标和名称 -->
      <div class="file-header">
        <div class="file-icon-large">
          <IconifyIconOnline
            :icon="getFileIcon(fileInfo)"
            :class="['icon', { 'folder-icon': fileInfo.isDirectory }]"
          />
        </div>
        <div class="file-name">
          <h3>{{ fileInfo.name }}</h3>
          <p class="file-path">{{ fileInfo.path }}</p>
        </div>
      </div>

      <!-- 基本信息 -->
      <div class="detail-section">
        <h4 class="section-title">基本信息</h4>
        <div class="detail-list">
          <div class="detail-item">
            <span class="label">类型:</span>
            <span class="value">{{ fileInfo.isDirectory ? "文件夹" : "文件" }}</span>
          </div>
          <div class="detail-item" v-if="!fileInfo.isDirectory">
            <span class="label">大小:</span>
            <span class="value">{{ formatFileSize(fileInfo.size) }}</span>
          </div>
          <div class="detail-item">
            <span class="label">修改时间:</span>
            <span class="value">{{ formatTime(fileInfo.modifiedTime) }}</span>
          </div>
          <div class="detail-item" v-if="fileInfo.permissions">
            <span class="label">权限:</span>
            <span class="value">{{ fileInfo.permissions }}</span>
          </div>
          <div class="detail-item" v-if="fileInfo.owner">
            <span class="label">所有者:</span>
            <span class="value">{{ fileInfo.owner }}</span>
          </div>
          <div class="detail-item" v-if="fileInfo.group">
            <span class="label">用户组:</span>
            <span class="value">{{ fileInfo.group }}</span>
          </div>
        </div>
      </div>

      <!-- 文件扩展信息 -->
      <div class="detail-section" v-if="!fileInfo.isDirectory">
        <h4 class="section-title">文件信息</h4>
        <div class="detail-list">
          <div class="detail-item">
            <span class="label">扩展名:</span>
            <span class="value">{{ getFileExtension(fileInfo.name) || "无" }}</span>
          </div>
          <div class="detail-item">
            <span class="label">MIME类型:</span>
            <span class="value">{{ getMimeType(fileInfo.name) }}</span>
          </div>
          <div class="detail-item">
            <span class="label">可预览:</span>
            <span class="value">{{ isPreviewable(fileInfo) ? "是" : "否" }}</span>
          </div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="detail-section">
        <h4 class="section-title">操作</h4>
        <div class="action-buttons">
          <el-button
            v-if="!fileInfo.isDirectory"
            type="primary"
            size="small"
            @click="downloadFile"
          >
            <IconifyIconOnline icon="ri:download-line" class="mr-1" />
            下载
          </el-button>
          
          <el-button
            v-if="!fileInfo.isDirectory && isPreviewable(fileInfo)"
            size="small"
            @click="previewFile"
          >
            <IconifyIconOnline icon="ri:eye-line" class="mr-1" />
            预览
          </el-button>
          
          <el-button size="small" @click="renameFile">
            <IconifyIconOnline icon="ri:edit-line" class="mr-1" />
            重命名
          </el-button>
          
          <el-button size="small" @click="copyFile">
            <IconifyIconOnline icon="ri:file-copy-line" class="mr-1" />
            复制
          </el-button>
          
          <el-button size="small" @click="moveFile">
            <IconifyIconOnline icon="ri:scissors-line" class="mr-1" />
            移动
          </el-button>
          
          <el-button size="small" type="danger" @click="deleteFile">
            <IconifyIconOnline icon="ri:delete-bin-line" class="mr-1" />
            删除
          </el-button>
        </div>
      </div>

      <!-- 目录内容 -->
      <div class="detail-section" v-if="fileInfo.isDirectory">
        <h4 class="section-title">目录内容</h4>
        <div class="directory-stats" v-loading="loadingStats">
          <div class="stat-item">
            <span class="label">子文件夹:</span>
            <span class="value">{{ directoryStats.folders }}</span>
          </div>
          <div class="stat-item">
            <span class="label">文件:</span>
            <span class="value">{{ directoryStats.files }}</span>
          </div>
          <div class="stat-item">
            <span class="label">总大小:</span>
            <span class="value">{{ formatFileSize(directoryStats.totalSize) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 重命名对话框 -->
    <el-dialog
      v-model="renameVisible"
      title="重命名"
      width="400px"
      :close-on-click-modal="false"
    >
      <el-form :model="renameForm" label-width="80px">
        <el-form-item label="新名称">
          <el-input
            v-model="renameForm.newName"
            placeholder="请输入新名称"
            @keyup.enter="confirmRename"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmRename">确定</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { formatBytes } from "@pureadmin/utils";
import dayjs from "dayjs";
import type { FileInfo } from "@/api/file-management";
import {
  downloadFile as downloadFileApi,
  renameFile as renameFileApi,
  deleteFile as deleteFileApi,
} from "@/api/file-management";

// Props
const props = defineProps<{
  visible: boolean;
  serverId: number;
  fileInfo: FileInfo | null;
}>();

// Emits
const emit = defineEmits<{
  "update:visible": [value: boolean];
  "file-action": [action: string, file: FileInfo];
}>();

// 响应式数据
const loadingStats = ref(false);
const renameVisible = ref(false);
const renameForm = reactive({
  newName: "",
});

const directoryStats = reactive({
  folders: 0,
  files: 0,
  totalSize: 0,
});

// 计算属性
const drawerVisible = computed({
  get: () => props.visible,
  set: (value) => emit("update:visible", value),
});

const drawerTitle = computed(() => {
  if (!props.fileInfo) return "文件详情";
  return props.fileInfo.isDirectory ? "文件夹详情" : "文件详情";
});

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
 * 获取文件图标
 */
const getFileIcon = (file: FileInfo) => {
  if (file.isDirectory) {
    return "ri:folder-line";
  }
  
  const ext = file.name.split(".").pop()?.toLowerCase();
  switch (ext) {
    case "js":
    case "ts":
    case "jsx":
    case "tsx":
      return "ri:javascript-line";
    case "vue":
      return "ri:vuejs-line";
    case "html":
      return "ri:html5-line";
    case "css":
    case "scss":
    case "sass":
      return "ri:css3-line";
    case "json":
      return "ri:file-code-line";
    case "md":
      return "ri:markdown-line";
    case "txt":
      return "ri:file-text-line";
    case "pdf":
      return "ri:file-pdf-line";
    case "zip":
    case "rar":
    case "7z":
      return "ri:file-zip-line";
    case "jpg":
    case "jpeg":
    case "png":
    case "gif":
    case "svg":
      return "ri:image-line";
    default:
      return "ri:file-line";
  }
};

/**
 * 获取文件扩展名
 */
const getFileExtension = (filename: string) => {
  return filename.split(".").pop()?.toLowerCase();
};

/**
 * 获取MIME类型
 */
const getMimeType = (filename: string) => {
  const ext = getFileExtension(filename);
  const mimeTypes: Record<string, string> = {
    txt: "text/plain",
    html: "text/html",
    css: "text/css",
    js: "application/javascript",
    json: "application/json",
    pdf: "application/pdf",
    jpg: "image/jpeg",
    jpeg: "image/jpeg",
    png: "image/png",
    gif: "image/gif",
    svg: "image/svg+xml",
    zip: "application/zip",
    rar: "application/x-rar-compressed",
  };
  return mimeTypes[ext || ""] || "application/octet-stream";
};

/**
 * 判断文件是否可预览
 */
const isPreviewable = (file: FileInfo) => {
  const ext = getFileExtension(file.name);
  const previewableExts = [
    "txt", "md", "json", "xml", "html", "css", "js", "ts", "vue",
    "jsx", "tsx", "py", "java", "c", "cpp", "h", "hpp", "php",
    "rb", "go", "rs", "sh", "bat", "yml", "yaml", "ini", "conf",
    "jpg", "jpeg", "png", "gif", "svg", "bmp", "webp"
  ];
  return previewableExts.includes(ext || "");
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
 * 预览文件
 */
const previewFile = () => {
  if (props.fileInfo) {
    emit("file-action", "preview", props.fileInfo);
  }
};

/**
 * 重命名文件
 */
const renameFile = () => {
  if (props.fileInfo) {
    renameForm.newName = props.fileInfo.name;
    renameVisible.value = true;
  }
};

/**
 * 确认重命名
 */
const confirmRename = async () => {
  if (!props.fileInfo || !renameForm.newName.trim()) {
    ElMessage.warning("请输入新名称");
    return;
  }

  if (renameForm.newName === props.fileInfo.name) {
    renameVisible.value = false;
    return;
  }

  try {
    const res = await renameFileApi(
      props.serverId,
      props.fileInfo.path,
      renameForm.newName
    );
    
    if (res.code === "00000" && res.data?.success) {
      ElMessage.success("重命名成功");
      renameVisible.value = false;
      emit("file-action", "rename", props.fileInfo);
    } else {
      ElMessage.error(res.data?.message || "重命名失败");
    }
  } catch (error) {
    console.error("重命名失败:", error);
    ElMessage.error("重命名失败");
  }
};

/**
 * 复制文件
 */
const copyFile = () => {
  if (props.fileInfo) {
    emit("file-action", "copy", props.fileInfo);
  }
};

/**
 * 移动文件
 */
const moveFile = () => {
  if (props.fileInfo) {
    emit("file-action", "move", props.fileInfo);
  }
};

/**
 * 删除文件
 */
const deleteFile = async () => {
  if (!props.fileInfo) return;

  try {
    await ElMessageBox.confirm(
      `确定要删除 "${props.fileInfo.name}" 吗？`,
      "删除确认",
      {
        type: "warning",
        confirmButtonText: "确定",
        cancelButtonText: "取消",
      }
    );

    const res = await deleteFileApi(
      props.serverId,
      props.fileInfo.path,
      props.fileInfo.isDirectory
    );
    
    if (res.code === "00000" && res.data?.success) {
      ElMessage.success("删除成功");
      drawerVisible.value = false;
      emit("file-action", "delete", props.fileInfo);
    } else {
      ElMessage.error(res.data?.message || "删除失败");
    }
  } catch (error) {
    // 用户取消或删除失败
    if (error !== "cancel") {
      console.error("删除失败:", error);
      ElMessage.error("删除失败");
    }
  }
};

/**
 * 加载目录统计信息
 */
const loadDirectoryStats = async () => {
  if (!props.fileInfo?.isDirectory) return;

  try {
    loadingStats.value = true;
    // 这里可以调用API获取目录统计信息
    // 暂时使用模拟数据
    directoryStats.folders = 5;
    directoryStats.files = 12;
    directoryStats.totalSize = 1024 * 1024 * 2.5; // 2.5MB
  } catch (error) {
    console.error("加载目录统计失败:", error);
  } finally {
    loadingStats.value = false;
  }
};
</script>

<style scoped>
.file-detail {
  padding: 0;
}

.file-header {
  display: flex;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-extra-light);
}

.file-icon-large {
  margin-right: 16px;
}

.file-icon-large .icon {
  font-size: 48px;
  color: var(--el-color-primary);
}

.file-icon-large .folder-icon {
  color: var(--el-color-warning);
}

.file-name h3 {
  margin: 0 0 4px 0;
  font-size: 18px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.file-path {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

.detail-section {
  padding: 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.detail-section:last-child {
  border-bottom: none;
}

.section-title {
  margin: 0 0 16px 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.detail-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.detail-item .label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  min-width: 80px;
}

.detail-item .value {
  font-size: 13px;
  color: var(--el-text-color-primary);
  text-align: right;
  word-break: break-all;
}

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.action-buttons .el-button {
  justify-content: flex-start;
}

.directory-stats {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--el-fill-color-extra-light);
  border-radius: 4px;
}

.stat-item .label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stat-item .value {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

/* 深色模式适配 */
:deep(.el-drawer) {
  background: var(--el-bg-color);
}

:deep(.el-drawer__header) {
  background: var(--el-fill-color-extra-light);
  border-bottom: 1px solid var(--el-border-color-light);
}

:deep(.el-drawer__body) {
  padding: 0;
}
</style>
