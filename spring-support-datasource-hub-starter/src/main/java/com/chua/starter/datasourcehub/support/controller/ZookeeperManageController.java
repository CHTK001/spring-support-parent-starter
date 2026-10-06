package com.chua.starter.datasourcehub.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.ZkChildNodeVO;
import com.chua.starter.datasourcehub.support.model.ZkClusterNodeVO;
import com.chua.starter.datasourcehub.support.model.ZkNodeAclVO;
import com.chua.starter.datasourcehub.support.model.ZkNodeDetailVO;
import com.chua.starter.datasourcehub.support.model.ZkNodeVO;
import com.chua.starter.datasourcehub.support.model.ZookeeperTestVO;
import com.chua.starter.datasourcehub.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.datasourcehub.support.model.dto.ZkNodeDTO;
import com.chua.starter.datasourcehub.support.service.ZookeeperManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ZooKeeper 注册中心管理 REST 接口。
 *
 * <p>树按父路径按需展开，避免一次拉取整棵 ZooKeeper；{@code datasourceId} 可缺省，
 * 由服务端退化为第一个 zookeeper 数据源。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Tag(name = "监控管理", description = "ZooKeeper 注册中心管理")
@RestController
@RequestMapping("/ms/monitor/zookeeper")
@RequiredArgsConstructor
public class ZookeeperManageController {

    /**
     * ZooKeeper 管理服务
     */
    private final ZookeeperManageService zookeeperManageService;

    /**
     * 测试 ZooKeeper 连通性。
     *
     * @param request 测试请求，可为空
     * @return 测试结果
     */
    @Operation(summary = "测试 ZooKeeper 连接")
    @PostMapping("/test")
    public ReturnResult<ZookeeperTestVO> test(@RequestBody(required = false) MiddlewareTestDTO request) {
        return call("测试 ZooKeeper 连接", () -> zookeeperManageService.test(request));
    }

    /**
     * 按父路径展开子节点。
     *
     * @param datasourceId 数据源 ID，可空
     * @param path         父路径，可空视为根节点
     * @return 子节点列表
     */
    @Operation(summary = "ZooKeeper 节点树")
    @GetMapping("/tree")
    public ReturnResult<List<ZkNodeVO>> tree(@RequestParam(required = false) Long datasourceId,
                                             @RequestParam(required = false) String path) {
        return call("读取 ZooKeeper 节点树", () -> zookeeperManageService.tree(datasourceId, path));
    }

    /**
     * 读取节点详情。
     *
     * @param path         节点绝对路径
     * @param datasourceId 数据源 ID，可空
     * @return 节点详情
     */
    @Operation(summary = "ZooKeeper 节点详情")
    @GetMapping("/node")
    public ReturnResult<ZkNodeDetailVO> node(@RequestParam String path,
                                             @RequestParam(required = false) Long datasourceId) {
        return call("读取 ZooKeeper 节点", () -> zookeeperManageService.node(path, datasourceId));
    }

    /**
     * 读取子节点及其数据。
     *
     * @param path         父路径
     * @param datasourceId 数据源 ID，可空
     * @return 子节点列表
     */
    @Operation(summary = "ZooKeeper 子节点列表")
    @GetMapping("/children")
    public ReturnResult<List<ZkChildNodeVO>> children(@RequestParam String path,
                                                      @RequestParam(required = false) Long datasourceId) {
        return call("读取 ZooKeeper 子节点", () -> zookeeperManageService.children(path, datasourceId));
    }

    /**
     * 读取节点 ACL 列表。
     *
     * @param path         节点绝对路径
     * @param datasourceId 数据源 ID，可空
     * @return ACL 条目列表
     */
    @Operation(summary = "ZooKeeper 节点 ACL")
    @GetMapping("/node/acl")
    public ReturnResult<List<ZkNodeAclVO>> acl(@RequestParam String path,
                                               @RequestParam(required = false) Long datasourceId) {
        return call("读取 ZooKeeper 节点 ACL", () -> zookeeperManageService.acl(path, datasourceId));
    }

    /**
     * 采集集群各节点运行指标（四字命令 {@code mntr}）。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 每个集群节点一行指标
     */
    @Operation(summary = "ZooKeeper 集群信息")
    @GetMapping("/cluster")
    public ReturnResult<List<ZkClusterNodeVO>> cluster(@RequestParam(required = false) Long datasourceId) {
        return call("采集 ZooKeeper 集群信息", () -> zookeeperManageService.cluster(datasourceId));
    }

    /**
     * 创建节点。
     *
     * @param request 节点载荷
     * @return 是否成功
     */
    @Operation(summary = "创建 ZooKeeper 节点")
    @PostMapping("/node")
    public ReturnResult<Boolean> create(@RequestBody ZkNodeDTO request) {
        return call("创建 ZooKeeper 节点", () -> zookeeperManageService.create(request));
    }

    /**
     * 覆盖节点数据。
     *
     * @param request 节点载荷
     * @return 是否成功
     */
    @Operation(summary = "更新 ZooKeeper 节点")
    @PutMapping("/node")
    public ReturnResult<Boolean> update(@RequestBody ZkNodeDTO request) {
        return call("更新 ZooKeeper 节点", () -> zookeeperManageService.update(request));
    }

    /**
     * 删除节点。
     *
     * @param path         节点绝对路径
     * @param datasourceId 数据源 ID，可空
     * @param recursive    为真时递归删除所有子孙节点
     * @return 是否成功
     */
    @Operation(summary = "删除 ZooKeeper 节点")
    @DeleteMapping("/node")
    public ReturnResult<Boolean> delete(@RequestParam String path,
                                        @RequestParam(required = false) Long datasourceId,
                                        @RequestParam(required = false, defaultValue = "false") boolean recursive) {
        return call("删除 ZooKeeper 节点", () -> zookeeperManageService.delete(path, datasourceId, recursive));
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
            log.error("[zookeeper] {}失败", action, e);
            return ReturnResult.error(action + "失败: " + e.getMessage());
        }
    }
}
