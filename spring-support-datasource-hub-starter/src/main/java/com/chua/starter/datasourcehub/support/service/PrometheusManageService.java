package com.chua.starter.datasourcehub.support.service;

import com.chua.starter.datasourcehub.support.model.PrometheusApplyResultVO;
import com.chua.starter.datasourcehub.support.model.PrometheusCommandVO;
import com.chua.starter.datasourcehub.support.model.PrometheusConfigVO;
import com.chua.starter.datasourcehub.support.model.PrometheusDashboardVO;
import com.chua.starter.datasourcehub.support.model.PrometheusFileVO;
import com.chua.starter.datasourcehub.support.model.dto.PrometheusDashboardDTO;
import com.chua.starter.datasourcehub.support.model.dto.PrometheusSaveConfigDTO;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Prometheus 控制台服务。
 *
 * <p>两条线并行：</p>
 * <ul>
 *   <li><strong>查询线</strong>：走 Prometheus HTTP API（{@code /api/v1/*}），
 *       覆盖即时/范围查询、目标、规则、告警、标签；</li>
 *   <li><strong>配置线</strong>：参考 nginx 控制台，经 SSH 读写
 *       {@code prometheus.yml}（临时落盘 → promtool 校验 → 备份 → 覆盖 → 重载），
 *       再加一份「自定义看板」的整存整取与公开只读嵌入。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface PrometheusManageService {

    /**
     * 读取总览：可达性、版本、目标健康分布、告警/规则计数、配置路径。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 总览
     */
    Map<String, Object> overview(Long datasourceId);

    /**
     * 即时查询。
     *
     * @param datasourceId 数据源 ID，可空
     * @param query        PromQL
     * @param time         求值时刻（RFC3339 或 Unix 秒），可空
     * @return Prometheus 原始响应（status/data）
     */
    JsonNode query(Long datasourceId, String query, String time);

    /**
     * 范围查询。
     *
     * @param datasourceId 数据源 ID，可空
     * @param query        PromQL
     * @param start        起始时刻（RFC3339 或 Unix 秒）
     * @param end          结束时刻
     * @param step         步长（如 15s / 1m）
     * @return Prometheus 原始响应（status/data）
     */
    JsonNode queryRange(Long datasourceId, String query, String start, String end, String step);

    /**
     * 抓取目标。
     *
     * @param datasourceId 数据源 ID，可空
     * @return data 节点（activeTargets/droppedTargets）
     */
    JsonNode targets(Long datasourceId);

    /**
     * 规则列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @return data 节点（groups）
     */
    JsonNode rules(Long datasourceId);

    /**
     * 活跃告警。
     *
     * @param datasourceId 数据源 ID，可空
     * @return data 节点（alerts）
     */
    JsonNode alerts(Long datasourceId);

    /**
     * 标签名列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 标签名
     */
    List<String> labels(Long datasourceId);

    /**
     * 指定标签的取值列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @param name         标签名
     * @return 标签值
     */
    List<String> labelValues(Long datasourceId, String name);

    /**
     * 读取配置文件内容。
     *
     * @param datasourceId 数据源 ID，可空
     * @param path         文件路径，空则主配置文件
     * @return 文件内容
     */
    PrometheusConfigVO readConfig(Long datasourceId, String path);

    /**
     * 列出配置根下的 yml 文件（含 rules 子目录）。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 文件列表
     */
    List<PrometheusFileVO> listFiles(Long datasourceId);

    /**
     * 保存配置文件：临时落盘 → promtool 校验 → 备份 → 覆盖 → 重载。
     *
     * @param request 保存请求
     * @return 执行结果（含每一步）
     */
    PrometheusApplyResultVO saveConfig(PrometheusSaveConfigDTO request);

    /**
     * 触发配置重载（HTTP {@code POST /-/reload}，失败回退 SIGHUP）。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 执行结果
     */
    PrometheusCommandVO reload(Long datasourceId);

    /**
     * 列出备份文件。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 备份列表（新在前）
     */
    List<PrometheusFileVO> listBackups(Long datasourceId);

    /**
     * 从备份恢复。
     *
     * @param datasourceId 数据源 ID，可空
     * @param backupPath   备份文件路径
     * @return 执行结果
     */
    PrometheusApplyResultVO restoreBackup(Long datasourceId, String backupPath);

    /**
     * 读取自定义看板。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 看板（未保存过时 config 为空串）
     */
    PrometheusDashboardVO getDashboard(Long datasourceId);

    /**
     * 保存自定义看板（实时保存链路后端入口）。
     *
     * @param request 保存请求
     * @return 保存后的看板（含嵌入令牌）
     */
    PrometheusDashboardVO saveDashboard(PrometheusDashboardDTO request);

    /**
     * 重新生成嵌入令牌。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 看板
     */
    PrometheusDashboardVO regenerateToken(Long datasourceId);

    /**
     * 按嵌入令牌读取看板（公开只读，不需要登录态）。
     *
     * @param token 嵌入令牌
     * @return 看板
     */
    PrometheusDashboardVO embedDashboard(String token);

    /**
     * 嵌入场景的即时查询（凭令牌代理到对应数据源）。
     *
     * @param token 嵌入令牌
     * @param query PromQL
     * @param time  求值时刻，可空
     * @return Prometheus 原始响应
     */
    JsonNode embedQuery(String token, String query, String time);

    /**
     * 嵌入场景的范围查询（凭令牌代理到对应数据源）。
     *
     * @param token 嵌入令牌
     * @param query PromQL
     * @param start 起始时刻
     * @param end   结束时刻
     * @param step  步长
     * @return Prometheus 原始响应
     */
    JsonNode embedQueryRange(String token, String query, String start, String end, String step);
}
