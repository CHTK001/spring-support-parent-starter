package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.model.MiddlewareTestVO;
import com.chua.starter.monitor.support.model.NacosClusterNodeVO;
import com.chua.starter.monitor.support.model.NacosConfigDetailVO;
import com.chua.starter.monitor.support.model.NacosConfigPageVO;
import com.chua.starter.monitor.support.model.NacosInstanceVO;
import com.chua.starter.monitor.support.model.NacosNamespaceVO;
import com.chua.starter.monitor.support.model.NacosServiceVO;
import com.chua.starter.monitor.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.monitor.support.model.dto.NacosConfigKeyDTO;
import com.chua.starter.monitor.support.model.dto.NacosConfigQueryDTO;
import com.chua.starter.monitor.support.model.dto.NacosConfigSaveDTO;
import com.chua.starter.monitor.support.model.dto.NacosInstanceUpdateDTO;
import com.chua.starter.monitor.support.model.dto.NacosNamespaceDTO;
import com.chua.starter.monitor.support.service.NacosManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Supplier;

/**
 * Nacos 配置中心管理 REST 接口。
 *
 * <p>配置详情、发布、删除三个接口在页面上只带 dataId/group/namespace，不带数据源 ID，
 * 因此这三个参数的 {@code datasourceId} 允许缺省，由服务端退化为第一个 nacos 数据源。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Tag(name = "监控管理", description = "Nacos 配置中心管理")
@RestController
@RequestMapping("/ms/monitor/nacos")
@RequiredArgsConstructor
public class NacosManageController {

    /**
     * Nacos 管理服务
     */
    private final NacosManageService nacosManageService;

    /**
     * 测试 Nacos 连通性。
     *
     * @param request 测试请求，可为空
     * @return 测试结果
     */
    @Operation(summary = "测试 Nacos 连接")
    @PostMapping("/test")
    public ReturnResult<MiddlewareTestVO> test(@RequestBody(required = false) MiddlewareTestDTO request) {
        return call("测试 Nacos 连接", () -> nacosManageService.test(request));
    }

    /**
     * 查询命名空间列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 命名空间列表
     */
    @Operation(summary = "Nacos 命名空间列表")
    @GetMapping("/namespaces")
    public ReturnResult<List<NacosNamespaceVO>> namespaces(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Nacos 命名空间", () -> nacosManageService.namespaces(datasourceId));
    }

    /**
     * 新建命名空间。
     *
     * @param dto 命名空间载荷
     * @return 是否成功
     */
    @Operation(summary = "新建 Nacos 命名空间")
    @PostMapping("/namespace")
    public ReturnResult<Boolean> createNamespace(@RequestBody NacosNamespaceDTO dto) {
        return call("新建 Nacos 命名空间", () -> nacosManageService.createNamespace(dto));
    }

    /**
     * 编辑命名空间。
     *
     * @param dto 命名空间载荷
     * @return 是否成功
     */
    @Operation(summary = "编辑 Nacos 命名空间")
    @PutMapping("/namespace")
    public ReturnResult<Boolean> updateNamespace(@RequestBody NacosNamespaceDTO dto) {
        return call("编辑 Nacos 命名空间", () -> nacosManageService.updateNamespace(dto));
    }

    /**
     * 删除命名空间。
     *
     * @param datasourceId 数据源 ID，可空
     * @param namespaceId  命名空间 ID
     * @return 是否成功
     */
    @Operation(summary = "删除 Nacos 命名空间")
    @DeleteMapping("/namespace")
    public ReturnResult<Boolean> deleteNamespace(@RequestParam(required = false) Long datasourceId,
                                                 @RequestParam String namespaceId) {
        return call("删除 Nacos 命名空间", () -> nacosManageService.deleteNamespace(datasourceId, namespaceId));
    }

    /**
     * 分页查询配置列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @param namespace    命名空间标识，可空
     * @param group        分组精确过滤，可空
     * @param dataId       Data ID 关键字，可空
     * @param page         页码，从 1 开始
     * @param pageSize     每页条数
     * @return 配置分页结果
     */
    @Operation(summary = "Nacos 配置列表")
    @GetMapping("/configs")
    public ReturnResult<NacosConfigPageVO> configs(@RequestParam(required = false) Long datasourceId,
                                                   @RequestParam(required = false) String namespace,
                                                   @RequestParam(required = false) String group,
                                                   @RequestParam(required = false) String dataId,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer pageSize) {
        NacosConfigQueryDTO query = new NacosConfigQueryDTO(datasourceId, namespace, group, dataId, page, pageSize);
        return call("读取 Nacos 配置列表", () -> nacosManageService.configs(query));
    }

    /**
     * 读取配置详情。
     *
     * @param dataId       配置 Data ID
     * @param datasourceId 数据源 ID，可空
     * @param namespace    命名空间标识，可空
     * @param group        配置分组，可空
     * @return 配置详情
     */
    @Operation(summary = "Nacos 配置详情")
    @GetMapping("/config/{dataId}")
    public ReturnResult<NacosConfigDetailVO> configDetail(@PathVariable String dataId,
                                                          @RequestParam(required = false) Long datasourceId,
                                                          @RequestParam(required = false) String namespace,
                                                          @RequestParam(required = false) String group) {
        NacosConfigKeyDTO key = new NacosConfigKeyDTO(datasourceId, namespace, group);
        return call("读取 Nacos 配置", () -> nacosManageService.configDetail(dataId, key));
    }

    /**
     * 发布配置（新建或更新，支持 beta 发布）。
     *
     * @param config 配置内容
     * @return 是否成功
     */
    @Operation(summary = "发布 Nacos 配置")
    @PostMapping("/config")
    public ReturnResult<Boolean> saveConfig(@RequestBody NacosConfigSaveDTO config) {
        return call("发布 Nacos 配置", () -> nacosManageService.saveConfig(config));
    }

    /**
     * 删除配置。
     *
     * @param dataId       配置 Data ID
     * @param datasourceId 数据源 ID，可空
     * @param namespace    命名空间标识，可空
     * @param group        配置分组，可空
     * @return 是否成功
     */
    @Operation(summary = "删除 Nacos 配置")
    @DeleteMapping("/config/{dataId}")
    public ReturnResult<Boolean> deleteConfig(@PathVariable String dataId,
                                              @RequestParam(required = false) Long datasourceId,
                                              @RequestParam(required = false) String namespace,
                                              @RequestParam(required = false) String group) {
        NacosConfigKeyDTO key = new NacosConfigKeyDTO(datasourceId, namespace, group);
        return call("删除 Nacos 配置", () -> nacosManageService.deleteConfig(dataId, key));
    }

    /**
     * 查询服务列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @param namespace    命名空间标识，可空
     * @return 服务列表
     */
    @Operation(summary = "Nacos 服务列表")
    @GetMapping("/services")
    public ReturnResult<List<NacosServiceVO>> services(@RequestParam(required = false) Long datasourceId,
                                                       @RequestParam(required = false) String namespace) {
        return call("读取 Nacos 服务列表", () -> nacosManageService.services(datasourceId, namespace));
    }

    /**
     * 查询服务实例列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @param namespace    命名空间标识，可空
     * @param serviceName  服务名
     * @param groupName    分组，可空
     * @return 实例列表
     */
    @Operation(summary = "Nacos 服务实例列表")
    @GetMapping("/service/instances")
    public ReturnResult<List<NacosInstanceVO>> instances(@RequestParam(required = false) Long datasourceId,
                                                        @RequestParam(required = false) String namespace,
                                                        @RequestParam String serviceName,
                                                        @RequestParam(required = false) String groupName) {
        return call("读取 Nacos 服务实例",
                () -> nacosManageService.instances(datasourceId, namespace, serviceName, groupName));
    }

    /**
     * 更新实例（上下线或改权重）。
     *
     * @param dto 实例更新载荷
     * @return 是否成功
     */
    @Operation(summary = "更新 Nacos 服务实例")
    @PutMapping("/service/instance")
    public ReturnResult<Boolean> updateInstance(@RequestBody NacosInstanceUpdateDTO dto) {
        return call("更新 Nacos 服务实例", () -> nacosManageService.updateInstance(dto));
    }

    /**
     * 查询集群节点。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 节点列表
     */
    @Operation(summary = "Nacos 集群节点")
    @GetMapping("/cluster/nodes")
    public ReturnResult<List<NacosClusterNodeVO>> clusterNodes(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Nacos 集群节点", () -> nacosManageService.clusterNodes(datasourceId));
    }

    /**
     * 执行一次管理操作，把远端失败换成可读原因而不是 500 堆栈。
     *
     * @param action   操作描述，用于日志
     * @param supplier 业务调用
     * @param <T>      返回类型
     * @return 响应
     */
    private <T> ReturnResult<T> call(String action, Supplier<T> supplier) {
        try {
            return ReturnResult.ok(supplier.get());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[nacos] {}失败", action, e);
            return ReturnResult.error(action + "失败: " + e.getMessage());
        }
    }
}
