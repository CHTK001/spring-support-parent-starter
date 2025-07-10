<template>
  <div class="file-tree">
    <!-- 头部工具栏 -->
    <div class="tree-header">
      <div class="tree-title">
        <IconifyIconOnline icon="ri:folder-line" class="mr-2" />
        <span>目录结构</span>
      </div>
      <div class="tree-actions">
        <el-tooltip content="刷新目录树" placement="top">
          <el-button size="small" text @click="refreshTree">
            <IconifyIconOnline icon="ri:refresh-line" />
          </el-button>
        </el-tooltip>
        <el-tooltip content="展开所有" placement="top">
          <el-button size="small" text @click="expandAll">
            <IconifyIconOnline icon="ri:add-box-line" />
          </el-button>
        </el-tooltip>
        <el-tooltip content="折叠所有" placement="top">
          <el-button size="small" text @click="collapseAll">
            <IconifyIconOnline icon="ri:subtract-box-line" />
          </el-button>
        </el-tooltip>
      </div>
    </div>

    <!-- 文件树 -->
    <div class="tree-content" v-loading="loading">
      <el-tree
        ref="treeRef"
        :data="treeData"
        :props="treeProps"
        :load="loadNode"
        :lazy="true"
        :expand-on-click-node="false"
        :highlight-current="true"
        node-key="path"
        @node-click="handleNodeClick"
        @node-expand="handleNodeExpand"
        @node-collapse="handleNodeCollapse"
      >
        <template #default="{ node, data }">
          <div class="tree-node">
            <IconifyIconOnline
              :icon="getNodeIcon(data)"
              :class="['node-icon', { 'folder-icon': data.isDirectory }]"
            />
            <span class="node-label" :title="data.name">{{ data.name }}</span>
            <div class="node-actions" v-if="data.isDirectory" @click.stop>
              <el-tooltip content="新建文件夹" placement="top">
                <el-button
                  size="small"
                  text
                  @click="createFolder(data)"
                >
                  <IconifyIconOnline icon="ri:folder-add-line" />
                </el-button>
              </el-tooltip>
              <el-tooltip content="刷新" placement="top">
                <el-button
                  size="small"
                  text
                  @click="refreshNode(node, data)"
                >
                  <IconifyIconOnline icon="ri:refresh-line" />
                </el-button>
              </el-tooltip>
            </div>
          </div>
        </template>
      </el-tree>
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
import { ref, reactive, onMounted, nextTick } from "vue";
import { ElMessage, ElTree } from "element-plus";
import type { FileInfo } from "@/api/file-management";
import { getFileTree, createDirectory } from "@/api/file-management";

// Props
const props = defineProps<{
  serverId: number;
  currentPath?: string;
}>();

// Emits
const emit = defineEmits<{
  "node-click": [path: string, node: FileInfo];
  "refresh": [];
}>();

// 响应式数据
const loading = ref(false);
const treeRef = ref<InstanceType<typeof ElTree>>();
const treeData = ref<FileInfo[]>([]);
const createFolderVisible = ref(false);
const createFolderForm = reactive({
  name: "",
  parentPath: "",
});

// 树形组件配置
const treeProps = {
  children: "children",
  label: "name",
  isLeaf: (data: FileInfo) => !data.isDirectory,
};

/**
 * 加载根节点
 */
const loadRootNode = async () => {
  if (!props.serverId) return;

  try {
    loading.value = true;
    const res = await getFileTree(props.serverId, "/", 2, false);

    if (res.code === "00000" && res.data?.success && res.data.tree) {
      // 将根节点的子节点作为树的根数据
      treeData.value = res.data.tree.children || [];
    } else {
      ElMessage.error(res.data?.message || "加载目录树失败");
      treeData.value = [];
    }
  } catch (error) {
    console.error("加载目录树失败:", error);
    ElMessage.error("加载目录树失败");
    treeData.value = [];
  } finally {
    loading.value = false;
  }
};

/**
 * 懒加载节点
 */
const loadNode = async (node: any, resolve: Function) => {
  if (node.level === 0) {
    // 根节点，返回初始数据
    resolve(treeData.value);
    return;
  }

  const nodeData = node.data as FileInfo;
  if (!nodeData.isDirectory) {
    resolve([]);
    return;
  }

  try {
    const res = await getFileTree(props.serverId, nodeData.path, 1, false);
    
    if (res.code === "00000" && res.data?.success && res.data.tree) {
      resolve(res.data.tree.children || []);
    } else {
      resolve([]);
    }
  } catch (error) {
    console.error("加载子节点失败:", error);
    resolve([]);
  }
};

/**
 * 处理节点点击
 */
const handleNodeClick = (data: FileInfo) => {
  emit("node-click", data.path, data);
};

/**
 * 处理节点展开
 */
const handleNodeExpand = (data: FileInfo, node: any) => {
  console.log("节点展开:", data.path);
};

/**
 * 处理节点折叠
 */
const handleNodeCollapse = (data: FileInfo, node: any) => {
  console.log("节点折叠:", data.path);
};

/**
 * 获取节点图标
 */
const getNodeIcon = (data: FileInfo) => {
  if (data.isDirectory) {
    return "ri:folder-line";
  }
  
  const ext = data.name.split(".").pop()?.toLowerCase();
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
 * 刷新树
 */
const refreshTree = () => {
  loadRootNode();
  emit("refresh");
};

/**
 * 展开所有节点
 */
const expandAll = () => {
  // 遍历所有节点并展开
  const expandNodes = (nodes: any[]) => {
    nodes.forEach(node => {
      if (node.isDirectory) {
        treeRef.value?.setExpanded(node.path, true);
        if (node.children) {
          expandNodes(node.children);
        }
      }
    });
  };
  expandNodes(treeData.value);
};

/**
 * 折叠所有节点
 */
const collapseAll = () => {
  // 遍历所有节点并折叠
  const collapseNodes = (nodes: any[]) => {
    nodes.forEach(node => {
      if (node.isDirectory) {
        treeRef.value?.setExpanded(node.path, false);
        if (node.children) {
          collapseNodes(node.children);
        }
      }
    });
  };
  collapseNodes(treeData.value);
};

/**
 * 创建文件夹
 */
const createFolder = (parentNode: FileInfo) => {
  createFolderForm.name = "";
  createFolderForm.parentPath = parentNode.path;
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
    const folderPath = `${createFolderForm.parentPath}/${createFolderForm.name}`;
    const res = await createDirectory(props.serverId, folderPath, false);

    if (res.code === "00000" && res.data?.success) {
      ElMessage.success("文件夹创建成功");
      createFolderVisible.value = false;
      refreshTree();
    } else {
      ElMessage.error(res.data?.message || "创建文件夹失败");
    }
  } catch (error) {
    console.error("创建文件夹失败:", error);
    ElMessage.error("创建文件夹失败");
  }
};

/**
 * 刷新节点
 */
const refreshNode = async (node: any, data: FileInfo) => {
  // 重新加载节点的子节点
  node.loaded = false;
  node.expand();
};

/**
 * 设置当前选中的路径
 */
const setCurrentPath = (path: string) => {
  nextTick(() => {
    treeRef.value?.setCurrentKey(path);
  });
};

// 组件挂载时加载根节点
onMounted(() => {
  loadRootNode();
});

// 暴露方法
defineExpose({
  refreshTree,
  setCurrentPath,
});
</script>

<style scoped>
.file-tree {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-light);
}

.tree-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--el-fill-color-extra-light);
}

.tree-title {
  display: flex;
  align-items: center;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.tree-actions {
  display: flex;
  gap: 4px;
}

.tree-content {
  flex: 1;
  overflow: auto;
  padding: 8px;
}

.tree-node {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 2px 0;
}

.node-icon {
  margin-right: 6px;
  font-size: 16px;
  color: var(--el-color-primary);
}

.folder-icon {
  color: var(--el-color-warning);
}

.node-label {
  flex: 1;
  font-size: 14px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-actions {
  display: none;
  gap: 2px;
}

.tree-node:hover .node-actions {
  display: flex;
}

/* 自定义树形组件样式 */
:deep(.el-tree-node__content) {
  height: 32px;
  padding: 0 8px;
  border-radius: 6px;
  transition: all 0.2s;
}

:deep(.el-tree-node__content:hover) {
  background-color: var(--el-fill-color-light);
}

:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

:deep(.el-tree-node__expand-icon) {
  color: var(--el-text-color-secondary);
}

:deep(.el-tree-node__expand-icon.is-leaf) {
  color: transparent;
}
</style>
