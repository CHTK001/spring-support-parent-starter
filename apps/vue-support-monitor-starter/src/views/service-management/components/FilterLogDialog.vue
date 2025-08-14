<template>
  <el-dialog v-model="visible" title="过滤器处理日志" width="1000px" :close-on-click-modal="false">
    <div class="filters">
      <el-row :gutter="12">
        <el-col :span="6">
          <el-date-picker v-model="dateRange" type="datetimerange" start-placeholder="开始时间" end-placeholder="结束时间" size="small" style="width: 100%" />
        </el-col>
        <el-col :span="4">
          <el-input v-model="clientIp" size="small" placeholder="客户端IP" clearable />
        </el-col>
        <el-col :span="6">
          <el-input v-model="filterType" size="small" placeholder="过滤器类型(类名或类型)" clearable />
        </el-col>
        <el-col :span="4">
          <el-input v-model="processStatus" size="small" placeholder="处理状态" clearable />
        </el-col>
        <el-col :span="4" class="btns">
          <el-button size="small" type="primary" @click="search">查询</el-button>
          <el-button size="small" @click="reset">重置</el-button>
          <el-button size="small" @click="handleExport">导出</el-button>
          <el-button size="small" type="danger" plain @click="handleCleanup">清理</el-button>
        </el-col>
      </el-row>
    </div>

    <el-table :data="rows" stripe size="small" height="480">
      <el-table-column prop="storeTime" label="存储时间" width="170">
        <template #default="{ row }">{{ formatTime(row.storeTime) }}</template>
      </el-table-column>
      <el-table-column prop="accessTime" label="访问时间" width="170">
        <template #default="{ row }">{{ formatTime(row.accessTime) }}</template>
      </el-table-column>
      <el-table-column prop="filterType" label="过滤器类型" width="180" show-overflow-tooltip />
      <el-table-column prop="processStatus" label="处理状态" width="140" />
      <el-table-column prop="clientIp" label="IP地址" width="130" />
      <el-table-column prop="clientGeo" label="地理位置" min-width="180" show-overflow-tooltip />
      <el-table-column prop="durationMs" label="耗时(ms)" width="90" />
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[20, 50, 100, 200]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="load"
        @current-change="load"
      />
    </div>

    <template #footer>
      <el-button @click="visible=false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { ElMessageBox, ElMessage } from 'element-plus';
import { pageSystemServerLogs, exportSystemServerLogs, cleanupSystemServerLogs, type SystemServerLogItem } from '@/api/system-server-log';

const visible = ref(false);
const serverId = ref<number | undefined>();
const dateRange = ref<Date[]>([]);
const clientIp = ref('');
const filterType = ref('');
const processStatus = ref('');

const page = ref(1);
const size = ref(20);
const total = ref(0);
const rows = ref<SystemServerLogItem[]>([]);

function open(id?: number) {
  visible.value = true;
  serverId.value = id;
  page.value = 1;
  load();
}

function load() {
  pageSystemServerLogs({
    current: page.value,
    size: size.value,
    serverId: serverId.value,
    clientIp: clientIp.value || undefined,
    filterType: filterType.value || undefined,
    processStatus: processStatus.value || undefined,
    startTime: dateRange.value[0] ? formatQueryTime(dateRange.value[0]) : undefined,
    endTime: dateRange.value[1] ? formatQueryTime(dateRange.value[1]) : undefined,
  }).then(res => {
    if (res.success && res.data) {
      rows.value = res.data.records || [];
      total.value = res.data.total || 0;
    } else {
      rows.value = []; total.value = 0;
    }
  });
}

function search() { page.value = 1; load(); }
function reset() {
  dateRange.value = []; clientIp.value = ''; filterType.value = ''; processStatus.value = '';
  page.value = 1; load();
}

function handleExport() {
  exportSystemServerLogs({
    serverId: serverId.value,
    clientIp: clientIp.value || undefined,
    filterType: filterType.value || undefined,
    processStatus: processStatus.value || undefined,
    startTime: dateRange.value[0] ? formatQueryTime(dateRange.value[0]) : undefined,
    endTime: dateRange.value[1] ? formatQueryTime(dateRange.value[1]) : undefined,
  }).then((blob: any) => {
    // 兼容 http.request<Blob> 返回
    const data = blob?.data ?? blob;
    const url = URL.createObjectURL(data instanceof Blob ? data : new Blob([data]));
    const a = document.createElement('a');
    a.href = url; a.download = `filter-log-${Date.now()}.csv`; a.click();
    URL.revokeObjectURL(url);
  });
}

function handleCleanup() {
  ElMessageBox.prompt('请输入清理阈值时间（yyyy-MM-dd HH:mm:ss），将删除更早的日志', '清理历史日志', {
    confirmButtonText: '确定', cancelButtonText: '取消', inputPlaceholder: 'yyyy-MM-dd HH:mm:ss'
  }).then(({ value }) => {
    if (!value) return;
    cleanupSystemServerLogs(value).then(res => {
      if (res.success) { ElMessage.success('清理成功'); load(); } else { ElMessage.error(res.msg || '清理失败'); }
    });
  }).catch(() => {});
}

function formatTime(v?: string) { return v ? new Date(v).toLocaleString() : '-'; }
function formatQueryTime(d: Date) {
  const pad = (n: number) => (n < 10 ? '0' + n : n);
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

defineExpose({ open });
</script>

<style scoped>
.filters{margin-bottom:12px}
.btns{display:flex;gap:6px}
.pager{margin-top:12px;display:flex;justify-content:center}
</style>

