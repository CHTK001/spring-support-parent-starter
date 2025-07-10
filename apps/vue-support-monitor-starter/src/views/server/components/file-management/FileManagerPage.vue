<template>
  <div class="file-manager-page">
    <!-- 头部工具栏 -->
    <div class="manager-header">
      <div class="header-left">
        <IconifyIconOnline icon="ri:folder-line" class="mr-2" />
        <span class="title">文件管理 - {{ serverInfo?.name }}</span>
        <el-tag v-if="serverInfo" size="small" class="ml-2">
          {{ getFileManagementModeText(serverInfo.fileManagementMode) }}
        </el-tag>
      </div>
      <div class="header-right">
        <el-button size="small" @click="refreshAll">
          <IconifyIconOnline icon="ri:refresh-line" class="mr-1" />
          刷新
        </el-button>
        <el-button size="small" @click="$emit('close')">
          <IconifyIconOnline icon="ri:close-line" class="mr-1" />
          关闭
        </el-button>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="manager-content">
      <!-- 左侧文件树 -->
      <div class="left-panel">
        <FileTree
          ref="fileTreeRef"
          :server-id="serverId"
          :current-path="currentPath"
          @node-click="handleTreeNodeClick"
          @refresh="handleTreeRefresh"
        />
      </div>

      <!-- 分割线 -->
      <div class="splitter"></div>

      <!-- 右侧文件列表 -->
      <div class="right-panel">
        <FileList
          ref="fileListRef"
          :server-id="serverId"
          :current-path="currentPath"
          @path-change="handlePathChange"
          @file-select="handleFileSelect"
          @refresh="handleListRefresh"
        />
      </div>
    </div>

    <!-- 文件预览/编辑对话框 -->
    <FilePreviewDialog
      v-model:visible="previewVisible"
      :server-id="serverId"
      :file-info="selectedFile"
      @file-updated="handleFileUpdated"
    />

    <!-- 文件详情侧边栏 -->
    <FileDetailDrawer
      v-model:visible="detailVisible"
      :server-id="serverId"
      :file-info="selectedFile"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from "vue";
import { ElMessage } from "element-plus";
import type { FileInfo } from "@/api/file-management";
import FileTree from "./FileTree.vue";
import FileList from "./FileList.vue";
import FilePreviewDialog from "./FilePreviewDialog.vue";
import FileDetailDrawer from "./FileDetailDrawer.vue";

// Props
const props = defineProps<{
  serverId: number;
  serverInfo?: any;
}>();

// Emits
const emit = defineEmits<{
  close: [];
}>();

// 响应式数据
const currentPath = ref("/");
const selectedFile = ref<FileInfo | null>(null);
const previewVisible = ref(false);
const detailVisible = ref(false);

// 组件引用
const fileTreeRef = ref();
const fileListRef = ref();

/**
 * 获取文件管理模式文本
 */
const getFileManagementModeText = (mode: string) => {
  const modeMap: Record<string, string> = {
    LOCAL: "本地连接",
    SSH: "SSH连接",
    NODE: "NODE客户端",
    API: "API连接",
    NONE: "未启用",
  };
  return modeMap[mode] || mode;
};

/**
 * 处理树节点点击
 */
const handleTreeNodeClick = (path: string, node: FileInfo) => {
  if (node.isDirectory) {
    currentPath.value = path;
    // 同步树的当前选中状态
    fileTreeRef.value?.setCurrentPath(path);
  }
};

/**
 * 处理路径变化
 */
const handlePathChange = (path: string) => {
  currentPath.value = path;
  // 同步树的当前选中状态
  fileTreeRef.value?.setCurrentPath(path);
};

/**
 * 处理文件选择
 */
const handleFileSelect = (file: FileInfo) => {
  selectedFile.value = file;
  
  // 根据文件类型决定操作
  if (file.isDirectory) {
    // 目录：显示详情
    detailVisible.value = true;
  } else {
    // 文件：根据类型决定是否可以预览
    if (isPreviewableFile(file)) {
      previewVisible.value = true;
    } else {
      detailVisible.value = true;
    }
  }
};

/**
 * 判断文件是否可以预览
 */
const isPreviewableFile = (file: FileInfo) => {
  const ext = file.name.split(".").pop()?.toLowerCase();
  const previewableExts = [
    "txt", "md", "json", "xml", "html", "css", "js", "ts", "vue",
    "jsx", "tsx", "py", "java", "c", "cpp", "h", "hpp", "php",
    "rb", "go", "rs", "sh", "bat", "yml", "yaml", "ini", "conf"
  ];
  return previewableExts.includes(ext || "");
};

/**
 * 处理树刷新
 */
const handleTreeRefresh = () => {
  console.log("树刷新");
};

/**
 * 处理列表刷新
 */
const handleListRefresh = () => {
  console.log("列表刷新");
};

/**
 * 处理文件更新
 */
const handleFileUpdated = () => {
  // 刷新文件列表
  fileListRef.value?.refreshList();
  ElMessage.success("文件更新成功");
};

/**
 * 刷新所有
 */
const refreshAll = () => {
  fileTreeRef.value?.refreshTree();
  fileListRef.value?.refreshList();
};

/**
 * 处理键盘快捷键
 */
const handleKeydown = (event: KeyboardEvent) => {
  // F5 刷新
  if (event.key === "F5") {
    event.preventDefault();
    refreshAll();
  }
  
  // Ctrl+R 刷新
  if (event.ctrlKey && event.key === "r") {
    event.preventDefault();
    refreshAll();
  }
  
  // ESC 关闭对话框
  if (event.key === "Escape") {
    if (previewVisible.value) {
      previewVisible.value = false;
    } else if (detailVisible.value) {
      detailVisible.value = false;
    }
  }
};

// 生命周期
onMounted(() => {
  document.addEventListener("keydown", handleKeydown);
});

onUnmounted(() => {
  document.removeEventListener("keydown", handleKeydown);
});

// 暴露方法
defineExpose({
  refreshAll,
  setCurrentPath: (path: string) => {
    currentPath.value = path;
  },
});
</script>

<style scoped>
.file-manager-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color);
  overflow: hidden;
}

.manager-header {
  height: 60px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-extra-light);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
}

.title {
  font-size: 16px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.header-right {
  display: flex;
  gap: 8px;
}

.manager-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.left-panel {
  width: 300px;
  min-width: 250px;
  max-width: 500px;
  height: 100%;
  flex-shrink: 0;
  background: var(--el-bg-color);
}

.splitter {
  width: 1px;
  background: var(--el-border-color-light);
  cursor: col-resize;
  flex-shrink: 0;
  position: relative;
}

.splitter:hover {
  background: var(--el-color-primary);
}

.splitter::before {
  content: "";
  position: absolute;
  left: -2px;
  top: 0;
  width: 5px;
  height: 100%;
  background: transparent;
}

.right-panel {
  flex: 1;
  height: 100%;
  overflow: hidden;
  background: var(--el-bg-color);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .manager-content {
    flex-direction: column;
  }
  
  .left-panel {
    width: 100%;
    height: 200px;
    max-width: none;
  }
  
  .splitter {
    width: 100%;
    height: 1px;
    cursor: row-resize;
  }
  
  .splitter::before {
    left: 0;
    top: -2px;
    width: 100%;
    height: 5px;
  }
  
  .right-panel {
    flex: 1;
    width: 100%;
  }
}

/* 暗色主题适配 */
@media (prefers-color-scheme: dark) {
  .file-manager-page {
    background: var(--el-bg-color-page);
  }
  
  .manager-header {
    background: var(--el-bg-color);
    border-bottom-color: var(--el-border-color);
  }
  
  .left-panel,
  .right-panel {
    background: var(--el-bg-color);
  }
  
  .splitter {
    background: var(--el-border-color);
  }
}

/* 加载状态 */
.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

/* 滚动条样式 */
:deep(.el-scrollbar__wrap) {
  scrollbar-width: thin;
  scrollbar-color: var(--el-border-color-light) transparent;
}

:deep(.el-scrollbar__wrap::-webkit-scrollbar) {
  width: 6px;
  height: 6px;
}

:deep(.el-scrollbar__wrap::-webkit-scrollbar-thumb) {
  background: var(--el-border-color-light);
  border-radius: 3px;
}

:deep(.el-scrollbar__wrap::-webkit-scrollbar-thumb:hover) {
  background: var(--el-border-color);
}

:deep(.el-scrollbar__wrap::-webkit-scrollbar-track) {
  background: transparent;
}
</style>
