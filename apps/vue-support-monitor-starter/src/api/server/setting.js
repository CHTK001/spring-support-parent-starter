import request from '@/utils/request'

const API_PREFIX = '/api/monitor/server/setting'

export default {
  /**
   * 分页查询服务器配置设置
   */
  page(params) {
    return request({
      url: `${API_PREFIX}/page`,
      method: 'get',
      params
    })
  },

  /**
   * 根据ID查询服务器配置设置
   */
  getById(id) {
    return request({
      url: `${API_PREFIX}/${id}`,
      method: 'get'
    })
  },

  /**
   * 根据服务器ID查询配置
   */
  getByServerId(serverId) {
    return request({
      url: `${API_PREFIX}/server/${serverId}`,
      method: 'get'
    })
  },

  /**
   * 根据服务器ID获取配置，如果不存在则创建默认配置
   */
  getOrCreateByServerId(serverId) {
    return request({
      url: `${API_PREFIX}/server/${serverId}/or-create`,
      method: 'get'
    })
  },

  /**
   * 批量查询服务器配置
   */
  batchQuery(serverIds) {
    return request({
      url: `${API_PREFIX}/batch/query`,
      method: 'post',
      data: serverIds
    })
  },

  /**
   * 新增服务器配置设置
   */
  save(data) {
    return request({
      url: API_PREFIX,
      method: 'post',
      data
    })
  },

  /**
   * 修改服务器配置设置
   */
  update(data) {
    return request({
      url: API_PREFIX,
      method: 'put',
      data
    })
  },

  /**
   * 批量保存或更新配置
   */
  batchSave(settings) {
    return request({
      url: `${API_PREFIX}/batch/save`,
      method: 'post',
      data: settings
    })
  },

  /**
   * 删除服务器配置设置
   */
  delete(id) {
    return request({
      url: `${API_PREFIX}/${id}`,
      method: 'delete'
    })
  },

  /**
   * 根据服务器ID删除配置
   */
  deleteByServerId(serverId) {
    return request({
      url: `${API_PREFIX}/server/${serverId}`,
      method: 'delete'
    })
  },

  /**
   * 批量删除配置
   */
  batchDelete(ids) {
    return request({
      url: `${API_PREFIX}/batch`,
      method: 'delete',
      data: ids
    })
  },

  /**
   * 更新配置状态
   */
  updateStatus(serverId, status) {
    return request({
      url: `${API_PREFIX}/status/${serverId}`,
      method: 'put',
      params: { status }
    })
  },

  /**
   * 获取启用监控的服务器配置
   */
  getMonitorEnabledSettings() {
    return request({
      url: `${API_PREFIX}/monitor/enabled`,
      method: 'get'
    })
  },

  /**
   * 获取启用告警的服务器配置
   */
  getAlertEnabledSettings() {
    return request({
      url: `${API_PREFIX}/alert/enabled`,
      method: 'get'
    })
  },

  /**
   * 获取启用上报的服务器配置
   */
  getReportEnabledSettings(reportMethod) {
    return request({
      url: `${API_PREFIX}/report/enabled`,
      method: 'get',
      params: { reportMethod }
    })
  },

  /**
   * 获取配置统计信息
   */
  getStatistics() {
    return request({
      url: `${API_PREFIX}/statistics`,
      method: 'get'
    })
  },

  /**
   * 验证配置有效性
   */
  validate(setting) {
    return request({
      url: `${API_PREFIX}/validate`,
      method: 'post',
      data: setting
    })
  },

  /**
   * 测试告警配置
   */
  testAlert(serverId) {
    return request({
      url: `${API_PREFIX}/test/alert/${serverId}`,
      method: 'post'
    })
  },

  /**
   * 测试上报配置
   */
  testReport(serverId) {
    return request({
      url: `${API_PREFIX}/test/report/${serverId}`,
      method: 'post'
    })
  },

  /**
   * 测试连接配置
   */
  testConnection(serverId) {
    return request({
      url: `${API_PREFIX}/test/connection/${serverId}`,
      method: 'post'
    })
  },

  /**
   * 获取配置模板
   */
  getTemplate(templateType) {
    return request({
      url: `${API_PREFIX}/template/${templateType}`,
      method: 'get'
    })
  },

  /**
   * 应用配置模板
   */
  applyTemplate(templateType, serverIds) {
    return request({
      url: `${API_PREFIX}/template/${templateType}/apply`,
      method: 'post',
      data: serverIds
    })
  },

  /**
   * 复制配置
   */
  copySettings(sourceServerId, targetServerIds) {
    return request({
      url: `${API_PREFIX}/copy/${sourceServerId}`,
      method: 'post',
      data: targetServerIds
    })
  },

  /**
   * 重置为默认配置
   */
  resetToDefault(serverId) {
    return request({
      url: `${API_PREFIX}/reset/${serverId}`,
      method: 'post'
    })
  },

  /**
   * 导出配置
   */
  exportSettings(serverIds, format = 'json') {
    return request({
      url: `${API_PREFIX}/export`,
      method: 'post',
      data: serverIds,
      params: { format }
    })
  },

  /**
   * 导入配置
   */
  importSettings(configData, format = 'json', overwrite = false) {
    return request({
      url: `${API_PREFIX}/import`,
      method: 'post',
      params: { configData, format, overwrite }
    })
  },

  /**
   * 获取配置变更历史
   */
  getHistory(serverId, limit = 10) {
    return request({
      url: `${API_PREFIX}/history/${serverId}`,
      method: 'get',
      params: { limit }
    })
  },

  /**
   * 获取推荐配置
   */
  getRecommended(serverId) {
    return request({
      url: `${API_PREFIX}/recommend/${serverId}`,
      method: 'get'
    })
  },

  /**
   * 批量更新监控状态
   */
  batchUpdateMonitorEnabled(serverIds, enabled) {
    return request({
      url: `${API_PREFIX}/batch/monitor/enabled`,
      method: 'put',
      data: { serverIds, enabled }
    })
  },

  /**
   * 批量更新告警状态
   */
  batchUpdateAlertEnabled(serverIds, enabled) {
    return request({
      url: `${API_PREFIX}/batch/alert/enabled`,
      method: 'put',
      data: { serverIds, enabled }
    })
  },

  /**
   * 批量更新上报状态
   */
  batchUpdateReportEnabled(serverIds, enabled) {
    return request({
      url: `${API_PREFIX}/batch/report/enabled`,
      method: 'put',
      data: { serverIds, enabled }
    })
  }
}
