<template>
  <div class="file-list">
    <!-- 头部工具栏 -->
    <div class="list-header">
      <!-- 路径导航 -->
      <div class="path-navigation">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item
            v-for="(item, index) in pathItems"
            :key="index"
            :class="{ clickable: index < pathItems.length - 1 }"
            @click="navigateToPath(item.path)"
          >
            {{ item.name }}
          </el-breadcrumb-item>
        </el-breadcrumb>
      </div>

      <!-- 工具栏 -->
      <div class="toolbar">
        <!-- 视图切换 -->
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="list">
            <IconifyIconOnline icon="ri:list-check" />
          </el-radio-button>
          <el-radio-button value="grid">
            <IconifyIconOnline icon="ri:grid-line" />
          </el-radio-button>
        </el-radio-group>

        <el-divider direction="vertical" />

        <!-- 操作按钮 -->
        <el-button size="small" @click="refreshList">
          <IconifyIconOnline icon="ri:refresh-line" class="mr-1" />
          刷新
        </el-button>

        <el-button size="small" @click="createFolder">
          <IconifyIconOnline icon="ri:folder-add-line" class="mr-1" />
          新建文件夹
        </el-button>

        <!-- 文件上传 -->
        <el-upload
          ref="uploadRef"
          :action="uploadUrl"
          :headers="uploadHeaders"
          :data="uploadData"
          :before-upload="handleBeforeUpload"
          :on-success="handleUploadSuccess"
          :on-error="handleUploadError"
          :show-file-list="false"
          multiple
        >
          <el-button size="small">
            <IconifyIconOnline icon="ri:upload-line" class="mr-1" />
            上传文件
          </el-button>
        </el-upload>

        <el-divider direction="vertical" />

        <!-- 批量操作 -->
        <el-button
          size="small"
          :disabled="selectedFiles.length === 0"
          @click="batchDelete"
        >
          <IconifyIconOnline icon="ri:delete-bin-line" class="mr-1" />
          批量删除
        </el-button>
      </div>
    </div>

    <!-- 文件列表内容 -->
    <div class="list-content" v-loading="loading">
      <!-- 列表视图 -->
      <div v-if="viewMode === 'list'" class="list-view">
        <el-table
          :data="fileList"
          @selection-change="handleSelectionChange"
          @row-dblclick="handleRowDoubleClick"
          stripe
          height="100%"
        >
          <el-table-column type="selection" width="55" />
          
          <el-table-column label="名称" min-width="300">
            <template #default="{ row }">
              <div class="file-item" @click="handleFileClick(row)">
                <IconifyIconOnline
                  :icon="getFileIcon(row)"
                  :class="['file-icon', { 'folder-icon': row.isDirectory }]"
                />
                <span class="file-name">{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="大小" width="120" align="right">
            <template #default="{ row }">
              {{ row.isDirectory ? "-" : formatFileSize(row.size) }}
            </template>
          </el-table-column>

          <el-table-column label="修改时间" width="180">
            <template #default="{ row }">
              {{ formatTime(row.modifiedTime) }}
            </template>
          </el-table-column>

          <el-table-column label="权限" width="100">
            <template #default="{ row }">
              {{ row.permissions || "-" }}
            </template>
          </el-table-column>

          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-dropdown @command="(command) => handleFileAction(command, row)">
                <el-button size="small" text>
                  操作
                  <IconifyIconOnline icon="ri:arrow-down-s-line" />
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="download" v-if="!row.isDirectory">
                      <IconifyIconOnline icon="ri:download-line" class="mr-1" />
                      下载
                    </el-dropdown-item>
                    <el-dropdown-item command="rename">
                      <IconifyIconOnline icon="ri:edit-line" class="mr-1" />
                      重命名
                    </el-dropdown-item>
                    <el-dropdown-item command="copy">
                      <IconifyIconOnline icon="ri:file-copy-line" class="mr-1" />
                      复制
                    </el-dropdown-item>
                    <el-dropdown-item command="move">
                      <IconifyIconOnline icon="ri:scissors-line" class="mr-1" />
                      移动
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" divided>
                      <IconifyIconOnline icon="ri:delete-bin-line" class="mr-1" />
                      删除
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 网格视图 -->
      <div v-else class="grid-view">
        <div class="file-grid">
          <div
            v-for="file in fileList"
            :key="file.path"
            class="file-card"
            :class="{ selected: selectedFiles.includes(file) }"
            @click="handleFileClick(file)"
            @dblclick="handleRowDoubleClick(file)"
          >
            <div class="file-card-icon">
              <IconifyIconOnline
                :icon="getFileIcon(file)"
                :class="['file-icon', { 'folder-icon': file.isDirectory }]"
              />
            </div>
            <div class="file-card-name" :title="file.name">
              {{ file.name }}
            </div>
            <div class="file-card-info">
              <span v-if="!file.isDirectory">{{ formatFileSize(file.size) }}</span>
              <span>{{ formatTime(file.modifiedTime) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="fileList.length === 0 && !loading" class="empty-state">
        <IconifyIconOnline icon="ri:folder-open-line" class="empty-icon" />
        <p>此文件夹为空</p>
      </div>
    </div>

    <!-- 新建文件夹对话框 -->
    <el-dialog
      v-model="createFolderVisible"
      title="新建文件夹"
      width="400px"
      :close-on-click-modal="false"
    >
      <el-form :model="createFolderForm" label-width="80px">
        <el-form-item label="文件夹名">
          <el-input
            v-model="createFolderForm.name"
            placeholder="请输入文件夹名称"
            @keyup.enter="confirmCreateFolder"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createFolderVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCreateFolder">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { formatBytes } from "@pureadmin/utils";
import dayjs from "dayjs";
import type { FileInfo } from "@/api/file-management";
import {
  getFileList,
  uploadFile,
  downloadFile,
  createDirectory,
  deleteFile,
  renameFile,
  batchDeleteFiles,
} from "@/api/file-management";

// Props
const props = defineProps<{
  serverId: number;
  currentPath: string;
}>();

// Emits
const emit = defineEmits<{
  "path-change": [path: string];
  "file-select": [file: FileInfo];
  "refresh": [];
}>();

// 响应式数据
const loading = ref(false);
const viewMode = ref<"list" | "grid">("list");
const fileList = ref<FileInfo[]>([]);
const selectedFiles = ref<FileInfo[]>([]);
const createFolderVisible = ref(false);
const createFolderForm = reactive({
  name: "",
});

// 上传相关
const uploadRef = ref();
const uploadUrl = computed(() => `/api/v1/gen/file-management/upload/${props.serverId}`);
const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${localStorage.getItem("token")}`,
}));
const uploadData = computed(() => ({
  targetPath: props.currentPath,
  overwrite: false,
}));

// 路径导航
const pathItems = computed(() => {
  const parts = props.currentPath.split("/").filter(Boolean);
  const items = [{ name: "根目录", path: "/" }];
  
  let currentPath = "";
  parts.forEach(part => {
    currentPath += `/${part}`;
    items.push({ name: part, path: currentPath });
  });
  
  return items;
});

/**
 * 加载文件列表
 */
const loadFileList = async () => {
  if (!props.serverId) return;

  try {
    loading.value = true;
    const res = await getFileList(props.serverId, props.currentPath);

    if (res.code === "00000" && res.data?.success) {
      fileList.value = res.data.files || [];
    } else {
      ElMessage.error(res.data?.message || "加载文件列表失败");
      fileList.value = [];
    }
  } catch (error) {
    console.error("加载文件列表失败:", error);
    ElMessage.error("加载文件列表失败");
    fileList.value = [];
  } finally {
    loading.value = false;
  }
};

/**
 * 监听路径变化
 */
watch(
  () => props.currentPath,
  () => {
    loadFileList();
    selectedFiles.value = [];
  },
  { immediate: true }
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
 * 处理文件点击
 */
const handleFileClick = (file: FileInfo) => {
  emit("file-select", file);
};

/**
 * 处理文件双击
 */
const handleRowDoubleClick = (file: FileInfo) => {
  if (file.isDirectory) {
    emit("path-change", file.path);
  } else {
    // 对于文件，可以实现预览或下载
    handleFileAction("download", file);
  }
};

/**
 * 处理选择变化
 */
const handleSelectionChange = (selection: FileInfo[]) => {
  selectedFiles.value = selection;
};

/**
 * 导航到指定路径
 */
const navigateToPath = (path: string) => {
  emit("path-change", path);
};

/**
 * 刷新列表
 */
const refreshList = () => {
  loadFileList();
  emit("refresh");
};

/**
 * 创建文件夹
 */
const createFolder = () => {
  createFolderForm.name = "";
  createFolderVisible.value = true;
};

/**
 * 确认创建文件夹
 */
const confirmCreateFolder = async () => {
  if (!createFolderForm.name.trim()) {
    ElMessage.warning("请输入文件夹名称");
    return;
  }

  try {
    const folderPath = `${props.currentPath}/${createFolderForm.name}`;
    const res = await createDirectory(props.serverId, folderPath, false);

    if (res.code === "00000" && res.data?.success) {
      ElMessage.success("文件夹创建成功");
      createFolderVisible.value = false;
      loadFileList();
    } else {
      ElMessage.error(res.data?.message || "创建文件夹失败");
    }
  } catch (error) {
    console.error("创建文件夹失败:", error);
    ElMessage.error("创建文件夹失败");
  }
};

/**
 * 处理文件上传前
 */
const handleBeforeUpload = (file: File) => {
  console.log("准备上传文件:", file.name);
  return true;
};

/**
 * 处理上传成功
 */
const handleUploadSuccess = (response: any) => {
  ElMessage.success("文件上传成功");
  loadFileList();
};

/**
 * 处理上传失败
 */
const handleUploadError = (error: any) => {
  console.error("文件上传失败:", error);
  ElMessage.error("文件上传失败");
};

/**
 * 处理文件操作
 */
const handleFileAction = async (command: string, file: FileInfo) => {
  switch (command) {
    case "download":
      await downloadFileAction(file);
      break;
    case "rename":
      await renameFileAction(file);
      break;
    case "copy":
      ElMessage.info("复制功能开发中");
      break;
    case "move":
      ElMessage.info("移动功能开发中");
      break;
    case "delete":
      await deleteFileAction(file);
      break;
  }
};

/**
 * 下载文件
 */
const downloadFileAction = async (file: FileInfo) => {
  try {
    const res = await downloadFile(props.serverId, file.path);
    
    // 创建下载链接
    const url = window.URL.createObjectURL(new Blob([res.data]));
    const link = document.createElement("a");
    link.href = url;
    link.download = file.name;
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
 * 重命名文件
 */
const renameFileAction = async (file: FileInfo) => {
  try {
    const { value: newName } = await ElMessageBox.prompt(
      "请输入新的文件名",
      "重命名",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        inputValue: file.name,
      }
    );

    if (newName && newName !== file.name) {
      const res = await renameFile(props.serverId, file.path, newName);
      
      if (res.code === "00000" && res.data?.success) {
        ElMessage.success("重命名成功");
        loadFileList();
      } else {
        ElMessage.error(res.data?.message || "重命名失败");
      }
    }
  } catch (error) {
    // 用户取消
  }
};

/**
 * 删除文件
 */
const deleteFileAction = async (file: FileInfo) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除 "${file.name}" 吗？`,
      "删除确认",
      {
        type: "warning",
        confirmButtonText: "确定",
        cancelButtonText: "取消",
      }
    );

    const res = await deleteFile(props.serverId, file.path, file.isDirectory);
    
    if (res.code === "00000" && res.data?.success) {
      ElMessage.success("删除成功");
      loadFileList();
    } else {
      ElMessage.error(res.data?.message || "删除失败");
    }
  } catch (error) {
    // 用户取消
  }
};

/**
 * 批量删除
 */
const batchDelete = async () => {
  if (selectedFiles.value.length === 0) {
    ElMessage.warning("请选择要删除的文件");
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedFiles.value.length} 个文件吗？`,
      "批量删除确认",
      {
        type: "warning",
        confirmButtonText: "确定",
        cancelButtonText: "取消",
      }
    );

    const paths = selectedFiles.value.map(file => file.path);
    const res = await batchDeleteFiles(props.serverId, paths, true);
    
    if (res.code === "00000" && res.data?.success) {
      ElMessage.success("批量删除成功");
      selectedFiles.value = [];
      loadFileList();
    } else {
      ElMessage.error(res.data?.message || "批量删除失败");
    }
  } catch (error) {
    // 用户取消
  }
};

// 暴露方法
defineExpose({
  refreshList,
  loadFileList,
});
</script>

<style scoped>
.file-list {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color);
}

.list-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-extra-light);
}

.path-navigation {
  margin-bottom: 12px;
}

.path-navigation .clickable {
  cursor: pointer;
  color: var(--el-color-primary);
}

.path-navigation .clickable:hover {
  text-decoration: underline;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.list-content {
  flex: 1;
  overflow: hidden;
}

.list-view {
  height: 100%;
}

.file-item {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.file-icon {
  margin-right: 8px;
  font-size: 18px;
  color: var(--el-color-primary);
}

.folder-icon {
  color: var(--el-color-warning);
}

.file-name {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.grid-view {
  height: 100%;
  overflow: auto;
  padding: 16px;
}

.file-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 16px;
}

.file-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  background: var(--el-bg-color);
}

.file-card:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.file-card.selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.file-card-icon {
  margin-bottom: 8px;
}

.file-card-icon .file-icon {
  font-size: 32px;
}

.file-card-name {
  font-size: 12px;
  text-align: center;
  margin-bottom: 4px;
  word-break: break-all;
  line-height: 1.2;
  max-height: 2.4em;
  overflow: hidden;
}

.file-card-info {
  font-size: 10px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--el-text-color-secondary);
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  color: var(--el-text-color-placeholder);
}
</style>
