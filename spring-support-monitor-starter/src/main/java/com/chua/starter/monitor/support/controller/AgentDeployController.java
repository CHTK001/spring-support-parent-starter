package com.chua.starter.monitor.support.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.config.AgentDeployProperties;
import com.chua.starter.monitor.support.model.AgentConnectTestVO;
import com.chua.starter.monitor.support.model.AgentDeployStatusVO;
import com.chua.starter.monitor.support.model.AgentHealthVO;
import com.chua.starter.monitor.support.service.AgentDeployService;
import com.chua.starter.monitor.support.service.AgentHttpClient;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


/**
 * Agent 自动部署控制器
 * <p>
 * 只做参数与结果封装，安装/重装/升级/卸载的实际编排在 {@link AgentDeployService}，
 * 全过程进度经 socket 事件 {@code monitor/agent-deploy} 推给前端。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "Agent自动部署", description = "远程安装/重装/升级/卸载 Monitor Agent")
@RestController
@RequiredArgsConstructor
@RequestMapping(ServerConstants.API_PREFIX + "/host/agent")
public class AgentDeployController {

    /**
     * 部署生命周期服务
    */
    private final AgentDeployService deployService;

    /**
     * agent HTTP 客户端
    */
    private final AgentHttpClient agentClient;

    /**
     * 主机 映射器
    */
    private final MsServerHostMapper hostMapper;

    /**
     * 部署配置
    */
    private final AgentDeployProperties properties;

    /**
     * 自动部署 Agent
     *
     * @param id 主机标识
     * @return 受理结果
     */
    @Operation(summary = "自动部署 Agent")
    @PostMapping("/{id}/deploy")
    public ReturnResult<String> deploy(@PathVariable Long id) {
        return submit(id, AgentDeployService.Action.INSTALL);
    }

    /**
     * 重装 Agent
     *
     * @param id 主机标识
     * @return 受理结果
     */
    @Operation(summary = "重装 Agent")
    @PostMapping("/{id}/reinstall")
    public ReturnResult<String> reinstall(@PathVariable Long id) {
        return submit(id, AgentDeployService.Action.REINSTALL);
    }

    /**
     * 升级 Agent
     *
     * @param id 主机标识
     * @return 受理结果
     */
    @Operation(summary = "升级 Agent")
    @PostMapping("/{id}/upgrade")
    public ReturnResult<String> upgrade(@PathVariable Long id) {
        return submit(id, AgentDeployService.Action.UPGRADE);
    }

    /**
     * 卸载 Agent
     *
     * @param id 主机标识
     * @return 受理结果
     */
    @Operation(summary = "卸载 Agent")
    @PostMapping("/{id}/uninstall")
    public ReturnResult<String> uninstall(@PathVariable Long id) {
        return submit(id, AgentDeployService.Action.UNINSTALL);
    }

    /**
     * 查询 Agent 状态：台账记录 + 实时探活，供画像页与列表按钮判定。
     *
     * @param id 主机标识
     * @return 状态数据
     */
    @Operation(summary = "Agent状态")
    @GetMapping("/{id}/status")
    public ReturnResult<AgentDeployStatusVO> status(@PathVariable Long id) {
        MsServerHost host = hostMapper.selectById(id);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + id);
        }
        AgentHealthVO health = agentClient.health(host);
        return ReturnResult.ok(new AgentDeployStatusVO(id,
                host.getServerHostAgentInstalled(),
                host.getServerHostAgentEnabled(),
                host.getServerHostAgentVersion(),
                host.getServerHostAgentPort() == null ? properties.getPort() : host.getServerHostAgentPort(),
                host.getServerHostAgentTime(),
                host.getServerHostAgentError(),
                deployService.busy(id),
                health != null,
                health == null ? null : health.version(),
                health == null ? null : health.buildTime()));
    }

    /**
     * 测试 Agent 连通性，并在连通时顺手把台账纠正为已安装。
     *
     * @param id 主机标识
     * @return 连通结果
     */
    @Operation(summary = "测试Agent连接", description = "测试目标主机 Agent 是否可用")
    @GetMapping("/test/{id}")
    public ReturnResult<AgentConnectTestVO> testAgent(@PathVariable Long id) {
        MsServerHost host = hostMapper.selectById(id);
        if (host == null) {
            return ReturnResult.error("主机不存在");
        }
        AgentHealthVO health = agentClient.health(host);
        if (health == null) {
            host.setServerHostAgentError("连通性测试失败");
            hostMapper.updateById(host);
            return ReturnResult.ok(new AgentConnectTestVO(false, null,
                    "Agent 连接失败: " + agentClient.portOf(host) + " 端口无响应或不可达"));
        }
        host.setServerHostAgentInstalled(1);
        host.setServerHostAgentVersion(health.version());
        hostMapper.updateById(host);
        // 成功后清掉历史失败原因（updateById 跳过 null，必须显式置空）
        hostMapper.update(null, new LambdaUpdateWrapper<MsServerHost>()
                .eq(MsServerHost::getId, id)
                .set(MsServerHost::getServerHostAgentError, null));
        return ReturnResult.ok(new AgentConnectTestVO(true, health.version(), "Agent 连接成功"));
    }

    /**
     * 提交部署动作。
     *
     * @param id     主机标识
     * @param action 动作
     * @return 受理结果
     */
    private ReturnResult<String> submit(Long id, AgentDeployService.Action action) {
        return deployService.submit(id, action);
    }

    /**
     * 向目标主机注入 APM Java Agent（运行时字节码注入型 RuntimeAgent）。
     *
     * @param id 主机标识
     * @return 受理结果
     */
    @Operation(summary = "注入 APM JavaAgent", description = "投递 runtime-agent.jar 并写入 JAVA_TOOL_OPTIONS，目标 JVM 启动时自动加载字节码注入 Agent")
    @PostMapping("/{id}/inject-apm")
    public ReturnResult<String> injectApm(@PathVariable Long id) {
        return deployService.injectApmAgent(id);
    }

    /**
     * 移除目标主机已注入的 APM Java Agent。
     *
     * @param id 主机标识
     * @return 受理结果
     */
    @Operation(summary = "移除 APM JavaAgent", description = "清理远端 JAVA_TOOL_OPTIONS 注入条目与 apm-agent.jar")
    @PostMapping("/{id}/uninject-apm")
    public ReturnResult<String> uninjectApm(@PathVariable Long id) {
        return deployService.uninjectApmAgent(id);
    }

    /**
     * 查询目标主机 APM Java Agent 注入状态。
     *
     * @param id 主机标识
     * @return 注入状态
     */
    @Operation(summary = "APM JavaAgent 状态", description = "查询远端 APM JavaAgent 注入配置与 jar 路径")
    @GetMapping("/{id}/apm-status")
    public ReturnResult<Map<String, Object>> apmStatus(@PathVariable Long id) {
        return deployService.apmStatus(id);
    }
}
