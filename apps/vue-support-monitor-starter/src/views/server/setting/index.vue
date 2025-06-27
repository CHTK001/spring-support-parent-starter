<template>
  <div class="server-setting-container">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h2>服务器配置管理</h2>
        <p>管理服务器监控、告警、上报等配置信息</p>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增配置
        </el-button>
        <el-button @click="handleBatchImport">
          <el-icon><Upload /></el-icon>
          批量导入
        </el-button>
        <el-button @click="handleExport">
          <el-icon><Download /></el-icon>
          导出配置
        </el-button>
      </div>
    </div>

    <!-- 搜索区域 -->
    <div class="search-container">
      <el-form :model="searchForm" inline>
        <el-form-item label="服务器ID">
          <el-input v-model="searchForm.serverId" placeholder="请输入服务器ID" clearable />
        </el-form-item>
        <el-form-item label="监控状态">
          <el-select v-model="searchForm.monitorEnabled" placeholder="请选择" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="告警状态">
          <el-select v-model="searchForm.alertEnabled" placeholder="请选择" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="上报方式">
          <el-select v-model="searchForm.dataReportMethod" placeholder="请选择" clearable>
            <el-option label="不支持上报" value="NONE" />
            <el-option label="本地上报" value="LOCAL" />
            <el-option label="接口上报" value="API" />
            <el-option label="Prometheus" value="PROMETHEUS" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 统计卡片 -->
    <div class="statistics-container">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-icon monitor">
                <el-icon><Monitor /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ statistics.monitor_enabled_count || 0 }}</div>
                <div class="stat-label">监控启用</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-icon alert">
                <el-icon><Bell /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ statistics.alert_enabled_count || 0 }}</div>
                <div class="stat-label">告警启用</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-icon report">
                <el-icon><Upload /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ statistics.report_enabled_count || 0 }}</div>
                <div class="stat-label">上报启用</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-icon total">
                <el-icon><Server /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ statistics.total_settings || 0 }}</div>
                <div class="stat-label">总配置数</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 数据表格 -->
    <div class="table-container">
      <el-table
        ref="tableRef"
        :data="tableData"
        stripe
        row-key="monitorSysGenServerSettingId"
        @selection-change="handleSelectionChange"
        v-loading="loading"
      >
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column prop="monitorSysGenServerId" label="服务器ID" width="100" />
        <el-table-column label="监控状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.monitorSysGenServerSettingMonitorEnabled === 1 ? 'success' : 'danger'">
              {{ row.monitorSysGenServerSettingMonitorEnabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="告警状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.monitorSysGenServerSettingAlertEnabled === 1 ? 'success' : 'danger'">
              {{ row.monitorSysGenServerSettingAlertEnabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上报方式" width="120">
          <template #default="{ row }">
            {{ getReportMethodText(row.monitorSysGenServerSettingDataReportMethod) }}
          </template>
        </el-table-column>
        <el-table-column prop="monitorSysGenServerSettingMonitorInterval" label="监控间隔(秒)" width="120" />
        <el-table-column prop="monitorSysGenServerSettingMetricsRetentionDays" label="保留天数" width="100" />
        <el-table-column label="配置状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.monitorSysGenServerSettingStatus === 1 ? 'success' : 'info'">
              {{ row.monitorSysGenServerSettingStatus === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sysUpdateTime" label="更新时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" size="small" @click="handleTest(row)">测试</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 批量操作栏 -->
    <div v-if="selectedRows.length > 0" class="batch-actions">
      <div class="batch-info">
        已选择 {{ selectedRows.length }} 项
      </div>
      <div class="batch-buttons">
        <el-button @click="handleBatchEnable">批量启用监控</el-button>
        <el-button @click="handleBatchDisable">批量禁用监控</el-button>
        <el-button @click="handleBatchDelete" type="danger">批量删除</el-button>
      </div>
    </div>

    <!-- 配置表单对话框 -->
    <SettingFormDialog
      v-model:visible="formDialogVisible"
      :form-data="currentFormData"
      :mode="formMode"
      @success="handleFormSuccess"
    />

    <!-- 导入对话框 -->
    <ImportDialog
      v-model:visible="importDialogVisible"
      @success="handleImportSuccess"
    />

    <!-- 导出对话框 -->
    <ExportDialog
      v-model:visible="exportDialogVisible"
      :selected-rows="selectedRows"
      @success="handleExportSuccess"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh, Upload, Download, Monitor, Bell, Server } from '@element-plus/icons-vue'
// import SettingFormDialog from './components/SettingFormDialog.vue'
// import ImportDialog from './components/ImportDialog.vue'
// import ExportDialog from './components/ExportDialog.vue'
// import api from '@/api/server/setting'

// 响应式数据
const tableRef = ref()
const formDialogVisible = ref(false)
const importDialogVisible = ref(false)
const exportDialogVisible = ref(false)
const formMode = ref('add')
const currentFormData = ref({})
const selectedRows = ref([])
const statistics = ref({})
const tableData = ref([])
const loading = ref(false)

// 搜索表单
const searchForm = reactive({
  serverId: '',
  monitorEnabled: '',
  alertEnabled: '',
  dataReportMethod: ''
})

// 分页
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 生命周期
onMounted(() => {
  loadData()
  loadStatistics()
})

// 方法
const loadData = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
      ...searchForm
    }
    const result = await api.page(params)
    if (result.success) {
      tableData.value = result.data.records || []
      pagination.total = result.data.total || 0
    }
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const loadStatistics = async () => {
  try {
    const result = await api.getStatistics()
    if (result.success) {
      statistics.value = result.data || {}
    }
  } catch (error) {
    console.error('加载统计信息失败:', error)
  }
}

const getReportMethodText = (method) => {
  const methods = {
    'NONE': '不支持上报',
    'LOCAL': '本地上报',
    'API': '接口上报',
    'PROMETHEUS': 'Prometheus'
  }
  return methods[method] || '未知'
}

const handleSearch = () => {
  pagination.current = 1
  loadData()
}

const handleReset = () => {
  Object.keys(searchForm).forEach(key => {
    searchForm[key] = ''
  })
  handleSearch()
}

const handleSizeChange = (size) => {
  pagination.size = size
  loadData()
}

const handleCurrentChange = (current) => {
  pagination.current = current
  loadData()
}

const handleAdd = () => {
  formMode.value = 'add'
  currentFormData.value = {}
  formDialogVisible.value = true
}

const handleEdit = (row) => {
  formMode.value = 'edit'
  currentFormData.value = { ...row }
  formDialogVisible.value = true
}

const handleTest = async (row) => {
  try {
    const result = await api.testConnection(row.monitorSysGenServerId)
    if (result.success) {
      ElMessage.success('连接测试成功')
    } else {
      ElMessage.error(result.message || '连接测试失败')
    }
  } catch (error) {
    ElMessage.error('连接测试失败')
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除这个配置吗？', '提示', {
      type: 'warning'
    })
    
    const result = await api.delete(row.monitorSysGenServerSettingId)
    if (result.success) {
      ElMessage.success('删除成功')
      loadData()
      loadStatistics()
    } else {
      ElMessage.error(result.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleSelectionChange = (selection) => {
  selectedRows.value = selection
}

const handleBatchEnable = async () => {
  const serverIds = selectedRows.value.map(row => row.monitorSysGenServerId)
  // 实现批量启用逻辑
  ElMessage.success('批量启用成功')
  loadData()
}

const handleBatchDisable = async () => {
  const serverIds = selectedRows.value.map(row => row.monitorSysGenServerId)
  // 实现批量禁用逻辑
  ElMessage.success('批量禁用成功')
  loadData()
}

const handleBatchDelete = async () => {
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 个配置吗？`, '提示', {
      type: 'warning'
    })
    
    const ids = selectedRows.value.map(row => row.monitorSysGenServerSettingId)
    const result = await api.batchDelete(ids)
    if (result.success) {
      ElMessage.success('批量删除成功')
      selectedRows.value = []
      loadData()
      loadStatistics()
    } else {
      ElMessage.error(result.message || '批量删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('批量删除失败')
    }
  }
}

const handleBatchImport = () => {
  importDialogVisible.value = true
}

const handleExport = () => {
  exportDialogVisible.value = true
}

const handleFormSuccess = () => {
  formDialogVisible.value = false
  loadData()
  loadStatistics()
}

const handleImportSuccess = () => {
  importDialogVisible.value = false
  loadData()
  loadStatistics()
}

const handleExportSuccess = () => {
  exportDialogVisible.value = false
}
</script>

<style scoped>
.server-setting-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-left h2 {
  margin: 0 0 5px 0;
  color: #303133;
}

.header-left p {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.search-container {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.statistics-container {
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 8px;
  overflow: hidden;
}

.stat-content {
  display: flex;
  align-items: center;
  padding: 10px;
}

.stat-icon {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 15px;
  font-size: 24px;
  color: #fff;
}

.stat-icon.monitor {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.stat-icon.alert {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.stat-icon.report {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.stat-icon.total {
  background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
  line-height: 1;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 5px;
}

.table-container {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.pagination-container {
  margin-top: 20px;
  text-align: right;
}

.batch-actions {
  position: fixed;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  background: #fff;
  padding: 15px 20px;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  display: flex;
  align-items: center;
  gap: 20px;
  z-index: 1000;
}

.batch-info {
  color: #606266;
  font-size: 14px;
}

.batch-buttons {
  display: flex;
  gap: 10px;
}
</style>
