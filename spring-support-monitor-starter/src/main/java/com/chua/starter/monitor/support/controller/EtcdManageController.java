package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.model.EtcdKeyDetailVO;
import com.chua.starter.monitor.support.model.EtcdKeyVO;
import com.chua.starter.monitor.support.model.EtcdLeaseVO;
import com.chua.starter.monitor.support.model.EtcdMemberVO;
import com.chua.starter.monitor.support.model.EtcdStatusVO;
import com.chua.starter.monitor.support.model.EtcdTestVO;
import com.chua.starter.monitor.support.model.dto.EtcdKeyDTO;
import com.chua.starter.monitor.support.model.dto.EtcdKeyQueryDTO;
import com.chua.starter.monitor.support.model.dto.EtcdKeySaveDTO;
import com.chua.starter.monitor.support.model.dto.EtcdLeaseDTO;
import com.chua.starter.monitor.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.monitor.support.service.EtcdManageService;
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
 * etcd 配置中心管理 REST 接口。
 *
 * <p>键按前缀按需展开，避免一次拉全量；{@code datasourceId} 可缺省，由服务端退化为
 * 第一个 etcd 数据源。键名走**查询参数**而不是路径参数：etcd 的键普遍带 {@code /}，
 * 路径参数会被容器在匹配阶段就拒掉（同 Redis 的 {@code /key?key=} 契约）。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Tag(name = "监控管理", description = "etcd 配置中心管理")
@RestController
@RequestMapping("/ms/monitor/etcd")
@RequiredArgsConstructor
public class EtcdManageController {

    /**
     * etcd 管理服务
     */
    private final EtcdManageService etcdManageService;

    /**
     * 测试 etcd 连通性。
     *
     * @param request 测试请求，可为空
     * @return 测试结果
     */
    @Operation(summary = "测试 etcd 连接")
    @PostMapping("/test")
    public ReturnResult<EtcdTestVO> test(@RequestBody(required = false) MiddlewareTestDTO request) {
        return call("测试 etcd 连接", () -> etcdManageService.test(request));
    }

    /**
     * 读取服务端状态。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 服务端状态
     */
    @Operation(summary = "etcd 服务端状态")
    @GetMapping("/status")
    public ReturnResult<EtcdStatusVO> status(@RequestParam(required = false) Long datasourceId) {
        return call("读取 etcd 服务端状态", () -> etcdManageService.status(datasourceId));
    }

    /**
     * 按前缀列举键。
     *
     * @param datasourceId 数据源 ID，可空
     * @param prefix       前缀，可空
     * @param limit        条数上限，可空
     * @param keysOnly     只取键名，可空（默认 true）
     * @return 键列表
     */
    @Operation(summary = "etcd 键列表")
    @GetMapping("/keys")
    public ReturnResult<List<EtcdKeyVO>> keys(@RequestParam(required = false) Long datasourceId,
                                              @RequestParam(required = false) String prefix,
                                              @RequestParam(required = false) Integer limit,
                                              @RequestParam(required = false) Boolean keysOnly) {
        EtcdKeyQueryDTO query = new EtcdKeyQueryDTO(datasourceId, prefix, limit, keysOnly);
        return call("读取 etcd 键列表", () -> etcdManageService.keys(query));
    }

    /**
     * 读取键详情。
     *
     * @param key          键名
     * @param datasourceId 数据源 ID，可空
     * @return 键详情
     */
    @Operation(summary = "etcd 键详情")
    @GetMapping("/key")
    public ReturnResult<EtcdKeyDetailVO> key(@RequestParam String key,
                                             @RequestParam(required = false) Long datasourceId) {
        EtcdKeyDTO dto = new EtcdKeyDTO(datasourceId, key, false);
        return call("读取 etcd 键", () -> etcdManageService.key(key, dto));
    }

    /**
     * 写入键（新增与覆盖同一语义）。
     *
     * @param request 写入请求
     * @return 是否成功
     */
    @Operation(summary = "写入 etcd 键")
    @PutMapping("/key")
    public ReturnResult<Boolean> save(@RequestBody EtcdKeySaveDTO request) {
        return call("写入 etcd 键", () -> etcdManageService.save(request));
    }

    /**
     * 删除键。
     *
     * @param key          键名
     * @param datasourceId 数据源 ID，可空
     * @param prefix       为真时按前缀删除所有以 key 为前缀的键
     * @return 实际删除的键数量
     */
    @Operation(summary = "删除 etcd 键")
    @DeleteMapping("/key")
    public ReturnResult<Long> delete(@RequestParam String key,
                                     @RequestParam(required = false) Long datasourceId,
                                     @RequestParam(required = false, defaultValue = "false") boolean prefix) {
        EtcdKeyDTO dto = new EtcdKeyDTO(datasourceId, key, prefix);
        return call("删除 etcd 键", () -> etcdManageService.delete(key, dto));
    }

    /**
     * 列举租约。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 租约列表
     */
    @Operation(summary = "etcd 租约列表")
    @GetMapping("/leases")
    public ReturnResult<List<EtcdLeaseVO>> leases(@RequestParam(required = false) Long datasourceId) {
        return call("读取 etcd 租约列表", () -> etcdManageService.leases(datasourceId));
    }

    /**
     * 授予租约。
     *
     * @param request 授予请求
     * @return 新建的租约
     */
    @Operation(summary = "授予 etcd 租约")
    @PostMapping("/lease")
    public ReturnResult<EtcdLeaseVO> grantLease(@RequestBody EtcdLeaseDTO request) {
        return call("授予 etcd 租约", () -> etcdManageService.grantLease(request));
    }

    /**
     * 撤销租约。
     *
     * @param leaseId      租约 ID
     * @param datasourceId 数据源 ID，可空
     * @return 是否成功
     */
    @Operation(summary = "撤销 etcd 租约")
    @DeleteMapping("/lease")
    public ReturnResult<Boolean> revokeLease(@RequestParam Long leaseId,
                                             @RequestParam(required = false) Long datasourceId) {
        EtcdLeaseDTO dto = new EtcdLeaseDTO(datasourceId, leaseId, null);
        return call("撤销 etcd 租约", () -> etcdManageService.revokeLease(dto));
    }

    /**
     * 查询集群成员与健康。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 成员列表
     */
    @Operation(summary = "etcd 集群成员")
    @GetMapping("/members")
    public ReturnResult<List<EtcdMemberVO>> members(@RequestParam(required = false) Long datasourceId) {
        return call("读取 etcd 集群成员", () -> etcdManageService.members(datasourceId));
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
            log.error("[etcd] {}失败", action, e);
            return ReturnResult.error(action + "失败: " + e.getMessage());
        }
    }
}
