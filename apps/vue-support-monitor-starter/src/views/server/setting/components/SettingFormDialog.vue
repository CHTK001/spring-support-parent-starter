<template>
  <el-dialog
    v-model="dialogVisible"
    :title="mode === 'add' ? '新增配置' : '编辑配置'"
    width="80%"
    :before-close="handleClose"
    destroy-on-close
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="140px"
      class="setting-form"
    >
      <el-tabs v-model="activeTab" type="border-card">
        <!-- 基础配置 -->
        <el-tab-pane label="基础配置" name="basic">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="服务器ID" prop="monitorSysGenServerId">
                <el-input-number
                  v-model="form.monitorSysGenServerId"
                  :min="1"
                  placeholder="请输入服务器ID"
                  style="width: 100%"
                  :disabled="mode === 'edit'"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="配置状态">
                <el-switch
                  v-model="form.monitorSysGenServerSettingStatus"
                  :active-value="1"
                  :inactive-value="0"
                  active-text="启用"
                  inactive-text="禁用"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="启用监控">
                <el-switch
                  v-model="form.monitorSysGenServerSettingMonitorEnabled"
                  :active-value="1"
                  :inactive-value="0"
                  active-text="启用"
                  inactive-text="禁用"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="监控间隔(秒)" prop="monitorSysGenServerSettingMonitorInterval">
                <el-input-number
                  v-model="form.monitorSysGenServerSettingMonitorInterval"
                  :min="10"
                  :max="3600"
                  placeholder="监控间隔"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="数据收集频率(秒)">
                <el-input-number
                  v-model="form.monitorSysGenServerSettingDataCollectionFrequency"
                  :min="5"
                  :max="300"
                  placeholder="数据收集频率"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="指标保留天数">
                <el-input-number
                  v-model="form.monitorSysGenServerSettingMetricsRetentionDays"
                  :min="1"
                  :max="365"
                  placeholder="指标保留天数"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="配置描述">
            <el-input
              v-model="form.monitorSysGenServerSettingDescription"
              type="textarea"
              :rows="3"
              placeholder="请输入配置描述"
            />
          </el-form-item>
        </el-tab-pane>

        <!-- 上报配置 -->
        <el-tab-pane label="上报配置" name="report">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="启用上报">
                <el-switch
                  v-model="form.monitorSysGenServerSettingReportEnabled"
                  :active-value="1"
                  :inactive-value="0"
                  active-text="启用"
                  inactive-text="禁用"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="上报方式" prop="monitorSysGenServerSettingDataReportMethod">
                <el-select
                  v-model="form.monitorSysGenServerSettingDataReportMethod"
                  placeholder="请选择上报方式"
                  style="width: 100%"
                >
                  <el-option label="不支持上报" value="NONE" />
                  <el-option label="本地上报" value="LOCAL" />
                  <el-option label="接口上报" value="API" />
                  <el-option label="Prometheus" value="PROMETHEUS" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <div v-if="form.monitorSysGenServerSettingDataReportMethod === 'PROMETHEUS'">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="Prometheus地址">
                  <el-input
                    v-model="form.monitorSysGenServerSettingPrometheusHost"
                    placeholder="请输入Prometheus服务器地址"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="Prometheus端口">
                  <el-input-number
                    v-model="form.monitorSysGenServerSettingPrometheusPort"
                    :min="1"
                    :max="65535"
                    placeholder="端口号"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </div>
        </el-tab-pane>

        <!-- 告警配置 -->
        <el-tab-pane label="告警配置" name="alert">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="启用告警">
                <el-switch
                  v-model="form.monitorSysGenServerSettingAlertEnabled"
                  :active-value="1"
                  :inactive-value="0"
                  active-text="启用"
                  inactive-text="禁用"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="告警静默时间(分钟)">
                <el-input-number
                  v-model="form.monitorSysGenServerSettingAlertSilenceDuration"
                  :min="1"
                  :max="1440"
                  placeholder="告警静默时间"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="CPU告警阈值(%)">
                <el-input-number
                  v-model="form.monitorSysGenServerSettingCpuAlertThreshold"
                  :min="0"
                  :max="100"
                  :precision="1"
                  placeholder="CPU告警阈值"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="内存告警阈值(%)">
                <el-input-number
                  v-model="form.monitorSysGenServerSettingMemoryAlertThreshold"
                  :min="0"
                  :max="100"
                  :precision="1"
                  placeholder="内存告警阈值"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="磁盘告警阈值(%)">
                <el-input-number
                  v-model="form.monitorSysGenServerSettingDiskAlertThreshold"
                  :min="0"
                  :max="100"
                  :precision="1"
                  placeholder="磁盘告警阈值"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="响应时间阈值(毫秒)">
                <el-input-number
                  v-model="form.monitorSysGenServerSettingResponseTimeAlertThreshold"
                  :min="100"
                  :max="60000"
                  placeholder="响应时间阈值"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="告警通知方式">
                <el-select
                  v-model="form.monitorSysGenServerSettingAlertNotificationMethod"
                  placeholder="请选择通知方式"
                  style="width: 100%"
                >
                  <el-option label="邮件" value="EMAIL" />
                  <el-option label="短信" value="SMS" />
                  <el-option label="Webhook" value="WEBHOOK" />
                  <el-option label="钉钉" value="DINGTALK" />
                  <el-option label="微信" value="WECHAT" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="自动恢复通知">
                <el-switch
                  v-model="form.monitorSysGenServerSettingAutoRecoveryNotificationEnabled"
                  :active-value="1"
                  :inactive-value="0"
                  active-text="启用"
                  inactive-text="禁用"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="通知地址">
            <el-input
              v-model="form.monitorSysGenServerSettingAlertNotificationAddress"
              placeholder="请输入邮箱地址、手机号或Webhook URL"
            />
          </el-form-item>
        </el-tab-pane>

        <!-- Docker配置 -->
        <el-tab-pane label="Docker配置" name="docker">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="启用Docker">
                <el-switch
                  v-model="form.monitorSysGenServerSettingDockerEnabled"
                  :active-value="1"
                  :inactive-value="0"
                  active-text="启用"
                  inactive-text="禁用"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="连接方式">
                <el-select
                  v-model="form.monitorSysGenServerSettingDockerConnectionType"
                  placeholder="请选择连接方式"
                  style="width: 100%"
                >
                  <el-option label="命令行" value="SHELL" />
                  <el-option label="TCP连接" value="TCP" />
                  <el-option label="Unix Socket" value="SOCKET" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <div v-if="form.monitorSysGenServerSettingDockerConnectionType === 'TCP'">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="Docker主机">
                  <el-input
                    v-model="form.monitorSysGenServerSettingDockerHost"
                    placeholder="请输入Docker主机地址"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="Docker端口">
                  <el-input-number
                    v-model="form.monitorSysGenServerSettingDockerPort"
                    :min="1"
                    :max="65535"
                    placeholder="端口号"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="启用TLS">
                  <el-switch
                    v-model="form.monitorSysGenServerSettingDockerTlsEnabled"
                    :active-value="1"
                    :inactive-value="0"
                    active-text="启用"
                    inactive-text="禁用"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12" v-if="form.monitorSysGenServerSettingDockerTlsEnabled === 1">
                <el-form-item label="证书路径">
                  <el-input
                    v-model="form.monitorSysGenServerSettingDockerCertPath"
                    placeholder="请输入证书路径"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </div>

          <div v-if="form.monitorSysGenServerSettingDockerConnectionType === 'SOCKET'">
            <el-form-item label="Socket路径">
              <el-input
                v-model="form.monitorSysGenServerSettingDockerSocketPath"
                placeholder="请输入Unix Socket路径，如：/var/run/docker.sock"
              />
            </el-form-item>
          </div>
        </el-tab-pane>

        <!-- 代理配置 -->
        <el-tab-pane label="代理配置" name="proxy">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="启用代理">
                <el-switch
                  v-model="form.monitorSysGenServerSettingProxyEnabled"
                  :active-value="1"
                  :inactive-value="0"
                  active-text="启用"
                  inactive-text="禁用"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12" v-if="form.monitorSysGenServerSettingProxyEnabled === 1">
              <el-form-item label="代理类型">
                <el-select
                  v-model="form.monitorSysGenServerSettingProxyType"
                  placeholder="请选择代理类型"
                  style="width: 100%"
                >
                  <el-option label="HTTP代理" value="HTTP" />
                  <el-option label="SOCKS4代理" value="SOCKS4" />
                  <el-option label="SOCKS5代理" value="SOCKS5" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <div v-if="form.monitorSysGenServerSettingProxyEnabled === 1">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="代理主机">
                  <el-input
                    v-model="form.monitorSysGenServerSettingProxyHost"
                    placeholder="请输入代理服务器地址"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="代理端口">
                  <el-input-number
                    v-model="form.monitorSysGenServerSettingProxyPort"
                    :min="1"
                    :max="65535"
                    placeholder="端口号"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="代理用户名">
                  <el-input
                    v-model="form.monitorSysGenServerSettingProxyUsername"
                    placeholder="请输入用户名（可选）"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="代理密码">
                  <el-input
                    v-model="form.monitorSysGenServerSettingProxyPassword"
                    type="password"
                    placeholder="请输入密码（可选）"
                    show-password
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </div>
        </el-tab-pane>

        <!-- 高级配置 -->
        <el-tab-pane label="高级配置" name="advanced">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="连接超时(秒)">
                <el-input-number
                  v-model="form.monitorSysGenServerSettingConnectionTimeout"
                  :min="5"
                  :max="300"
                  placeholder="连接超时时间"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="读取超时(秒)">
                <el-input-number
                  v-model="form.monitorSysGenServerSettingReadTimeout"
                  :min="10"
                  :max="600"
                  placeholder="读取超时时间"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="性能优化建议">
                <el-switch
                  v-model="form.monitorSysGenServerSettingPerformanceSuggestionEnabled"
                  :active-value="1"
                  :inactive-value="0"
                  active-text="启用"
                  inactive-text="禁用"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="自定义标签">
            <el-input
              v-model="form.monitorSysGenServerSettingCustomTags"
              type="textarea"
              :rows="3"
              placeholder="请输入JSON格式的自定义标签，如：{&quot;env&quot;: &quot;prod&quot;, &quot;team&quot;: &quot;ops&quot;}"
            />
          </el-form-item>
        </el-tab-pane>
      </el-tabs>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="loading">
          {{ mode === 'add' ? '新增' : '更新' }}
        </el-button>
        <el-button v-if="mode === 'edit'" @click="handleTest">测试连接</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'

// Props
const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  formData: {
    type: Object,
    default: () => ({})
  },
  mode: {
    type: String,
    default: 'add' // add | edit
  }
})

// Emits
const emit = defineEmits(['update:visible', 'success'])

// 响应式数据
const formRef = ref()
const loading = ref(false)
const activeTab = ref('basic')

// 计算属性
const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value)
})

// 表单数据
const form = reactive({
  monitorSysGenServerId: null,
  monitorSysGenServerSettingStatus: 1,
  monitorSysGenServerSettingMonitorEnabled: 1,
  monitorSysGenServerSettingMonitorInterval: 60,
  monitorSysGenServerSettingDataCollectionFrequency: 30,
  monitorSysGenServerSettingMetricsRetentionDays: 30,
  monitorSysGenServerSettingReportEnabled: 1,
  monitorSysGenServerSettingDataReportMethod: 'API',
  monitorSysGenServerSettingAlertEnabled: 1,
  monitorSysGenServerSettingCpuAlertThreshold: 80,
  monitorSysGenServerSettingMemoryAlertThreshold: 85,
  monitorSysGenServerSettingDiskAlertThreshold: 90,
  monitorSysGenServerSettingResponseTimeAlertThreshold: 5000,
  monitorSysGenServerSettingAlertNotificationMethod: 'EMAIL',
  monitorSysGenServerSettingAlertSilenceDuration: 30,
  monitorSysGenServerSettingAutoRecoveryNotificationEnabled: 1,
  monitorSysGenServerSettingDockerEnabled: 0,
  monitorSysGenServerSettingDockerConnectionType: 'SHELL',
  monitorSysGenServerSettingDockerTlsEnabled: 0,
  monitorSysGenServerSettingProxyEnabled: 0,
  monitorSysGenServerSettingProxyType: 'HTTP',
  monitorSysGenServerSettingConnectionTimeout: 30,
  monitorSysGenServerSettingReadTimeout: 60,
  monitorSysGenServerSettingPerformanceSuggestionEnabled: 1,
  monitorSysGenServerSettingDescription: '',
  monitorSysGenServerSettingCustomTags: ''
})

// 表单验证规则
const rules = {
  monitorSysGenServerId: [
    { required: true, message: '请输入服务器ID', trigger: 'blur' }
  ],
  monitorSysGenServerSettingMonitorInterval: [
    { required: true, message: '请输入监控间隔', trigger: 'blur' }
  ],
  monitorSysGenServerSettingDataReportMethod: [
    { required: true, message: '请选择上报方式', trigger: 'change' }
  ]
}

// 监听表单数据变化
watch(() => props.formData, (newData) => {
  if (newData && Object.keys(newData).length > 0) {
    Object.assign(form, newData)
  }
}, { immediate: true, deep: true })

// 方法
const handleClose = () => {
  dialogVisible.value = false
  resetForm()
}

const resetForm = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  activeTab.value = 'basic'
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()
    loading.value = true
    
    // 这里调用API保存数据
    // const result = await api.save(form)
    // if (result.success) {
      ElMessage.success(props.mode === 'add' ? '新增成功' : '更新成功')
      emit('success')
    // }
  } catch (error) {
    console.error('表单验证失败:', error)
  } finally {
    loading.value = false
  }
}

const handleTest = async () => {
  try {
    loading.value = true
    // 这里调用API测试连接
    // const result = await api.testConnection(form)
    ElMessage.success('连接测试成功')
  } catch (error) {
    ElMessage.error('连接测试失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.setting-form {
  max-height: 600px;
  overflow-y: auto;
}

.dialog-footer {
  text-align: right;
}

:deep(.el-tabs__content) {
  padding: 20px;
}

:deep(.el-form-item) {
  margin-bottom: 20px;
}
</style>
