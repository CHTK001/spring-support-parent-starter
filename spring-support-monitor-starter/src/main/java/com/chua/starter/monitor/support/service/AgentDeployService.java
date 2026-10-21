package com.chua.starter.monitor.support.service;

import com.chua.common.support.lang.code.ReturnResult;

import java.nio.file.Path;
import java.util.Map;

/**
 * Agent 部署生命周期服务
 * <p>
 * 负责把 agent 装到目标主机、升级、重装与卸载，全过程以 socket 事件推进度。
 * 四个动作各自有真实语义：安装做前置探测与服务注册；升级做「传新包 → 停服务 → 备份 → 换装 →
 * 起服务 → 健康校验 → 失败回滚」；重装先彻底清理再全新安装；卸载停服、去自启、删文件并回写主机台账。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface AgentDeployService {

    /**
     * 部署动作
    */
    enum Action {

        /**
         * 首次安装
        */
        INSTALL,

        /**
         * 重装（先清理再装）
        */
        REINSTALL,

        /**
         * 升级（保留回滚点）
        */
        UPGRADE,

        /**
         * 卸载
        */
        UNINSTALL;

        /**
         * 按名称解析动作，非法值返回 null。
         *
         * @param value 名称（忽略大小写）
         * @return 动作
         */
        public static Action of(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            for (Action action : values()) {
                if (action.name().equalsIgnoreCase(value.trim())) {
                    return action;
                }
            }
            return null;
        }
    }

    /**
     * 提交一次部署动作（异步执行，立即返回）。
     *
     * @param hostId 主机标识
     * @param action 动作
     * @return 受理结果
     */
    ReturnResult<String> submit(Long hostId, Action action);

    /**
     * 该主机是否已有 agent 操作在跑。
     *
     * @param hostId 主机标识
     * @return true 表示执行中
     */
    boolean busy(Long hostId);

    /**
     * 部署使用的本地 agent 包路径，与下载端点共用同一套定位规则。
     *
     * @return 可读的 jar 路径
     */
    Path packageFile();

    /**
     * 向目标主机注入 APM Java Agent（运行时字节码注入型 RuntimeAgent）。
     *
     * @param hostId 主机标识
     * @return 受理结果
     */
    ReturnResult<String> injectApmAgent(Long hostId);

    /**
     * 移除目标主机已注入的 APM Java Agent 配置。
     *
     * @param hostId 主机标识
     * @return 受理结果
     */
    ReturnResult<String> uninjectApmAgent(Long hostId);

    /**
     * 查询目标主机 APM Java Agent 注入状态。
     *
     * @param hostId 主机标识
     * @return 状态结果
     */
    ReturnResult<Map<String, Object>> apmStatus(Long hostId);
}
