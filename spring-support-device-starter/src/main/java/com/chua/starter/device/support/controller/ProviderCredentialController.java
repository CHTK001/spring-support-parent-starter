package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.support.entity.DeviceProviderCredential;
import com.chua.starter.device.support.model.CredentialHealthResult;
import com.chua.starter.device.support.scheduler.DeviceScheduleManager;
import com.chua.starter.device.support.service.DeviceProviderCredentialService;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceSyncService;
import com.chua.starter.device.spi.model.DeviceConnectInfo;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 提供商接入凭据控制器
 * <p>管理厂商自定义的 AK/SK 等接入参数；同一厂商可登记多套凭据。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
// 说明：厂商 AK/SK 等接入参数的登记与维护（本模块未引入 swagger 注解）
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/device/provider-credential")
public class ProviderCredentialController {

    /** 单次批量检测最多覆盖的凭据数（防止一次点爆所有厂商平台） */
    private static final int MAX_TEST_BATCH = 50;

    /** 批量检测的并发上限 */
    private static final int MAX_TEST_CONCURRENCY = 4;

    /** 单套凭据探测超时（秒） */
    private static final int TEST_TIMEOUT_SECONDS = 20;

    /** 提供商接入凭据服务 */
    private final DeviceProviderCredentialService credentialService;

    /** 设备服务（凭据连通性测试） */
    private final DeviceService deviceService;

    /** 凭据维度同步服务 */
    private final DeviceSyncService deviceSyncService;

    /** 设备定时调度器（凭据级 Tick 开关/间隔热重载） */
    private final DeviceScheduleManager scheduleManager;

    @GetMapping("/list")
    /**
     * Request Param
     * @param vendor 字符串
     * @return 返回值
    */
    public ReturnResult<List<DeviceProviderCredential>> list(@RequestParam(required = false) String vendor) {
        return ReturnResult.ok(credentialService.list(vendor));
    }
    @GetMapping("/{id}")
    /**
     * detail
     * @param id 字符串
     * @return 返回值
    */
    public ReturnResult<DeviceProviderCredential> detail(@PathVariable String id) {
        return ReturnResult.ok(credentialService.get(id));
    }
    @PostMapping
    /**
     * 创建
     * @param credential 参数
     * @return 返回值
    */
    public ReturnResult<DeviceProviderCredential> create(@RequestBody DeviceProviderCredential credential) {
        DeviceProviderCredential created = credentialService.create(credential);
        // 新建时高级配置可能已经打开：立刻按配置挂 Tick（全关则什么都不注册）
        scheduleManager.refresh(created.getDeviceCredentialId());
        return ReturnResult.ok(created);
    }
    @PutMapping("/{id}")
    public ReturnResult<DeviceProviderCredential> update(@PathVariable String id,
                                                         @RequestBody DeviceProviderCredential credential) {
        DeviceProviderCredential updated = credentialService.update(id, credential);
        // 高级配置（开关/间隔）变更后：重扫该凭据的 Tick，下一轮调度生效
        scheduleManager.refresh(id);
        return ReturnResult.ok(updated);
    }

    /**
     * 只切换启用/停用，避免整对象更新把 AK/SK、默认套等字段一并覆盖。
     *
     * @param id      凭据 ID
     * @param enabled 是否启用
     * @return 更新后的凭据
     */
    @PutMapping("/{id}/enabled")
    public DeviceProviderCredential updateEnabled(@PathVariable String id,
                                                 @RequestParam boolean enabled) {
        DeviceProviderCredential updated = credentialService.updateEnabled(id, enabled);
        // 停用后该凭据的 Tick 一并下线，启用时按高级配置重新挂上
        scheduleManager.refresh(id);
        return updated;
    }

    @DeleteMapping("/{id}")
    /**
     * 删除
     * @param id 字符串
     * @return 返回值
    */
    public ReturnResult<Void> delete(@PathVariable String id) {
        credentialService.delete(id);
        // 凭据没了，它名下的 Tick 也要撤掉
        scheduleManager.refresh(id);
        return ReturnResult.ok();
    }

    /**
     * 测试凭据连通性
     *
     * <p>只验证平台登录/令牌，不需要具体设备；SDK 直连凭据没有 IP，始终返回 false。</p>
     *
     * @param id 凭据 ID
     * @return true 表示登录成功
     */
    @PostMapping("/{id}/test")
    public ReturnResult<Boolean> test(@PathVariable String id) {
        DeviceProviderCredential credential = credentialService.resolveById(id);
        if (credential == null) {
            throw new IllegalArgumentException("凭据不存在: " + id);
        }
        boolean ok = deviceService.testConnection(connectInfo(credential));
        // 单条测试同样留痕，凭据表的健康列因此不需要额外请求
        credentialService.recordTestResult(id, ok);
        return ReturnResult.ok(ok);
    }

    /**
     * 批量测试凭据连通性（凭据表「全部检测」）。
     *
     * <p>默认检测全部启用中的凭据，也可传入 ID 列表只测指定几条；并发上限
     * {@code 4}、单条超时 {@code 20} 秒、单次最多 {@code 50} 条，避免一次点击把
     * 所有厂商平台同时打满。每条结果都会写回凭据的「最近检测」两列。</p>
     *
     * @param ids 指定凭据 ID 列表，为 {@code null}/空 表示全部启用中的凭据
     * @return 每套凭据的探测结果（与请求顺序一致）
     */
    @PostMapping("/test-all")
    public ReturnResult<List<CredentialHealthResult>> testAll(
            @RequestBody(required = false) List<String> ids) {
        Set<String> wanted = (ids == null || ids.isEmpty()) ? null : new HashSet<>(ids);
        List<DeviceProviderCredential> targets = new ArrayList<>();
        for (DeviceProviderCredential credential : credentialService.list(null)) {
            if (Boolean.FALSE.equals(credential.getDeviceCredentialEnabled())) {
                continue;
            }
            if (wanted != null && !wanted.contains(credential.getDeviceCredentialId())) {
                continue;
            }
            if (targets.size() >= MAX_TEST_BATCH) {
                break;
            }
            targets.add(credential);
        }
        if (targets.isEmpty()) {
            return ReturnResult.ok(List.of());
        }

        int threads = Math.max(1, Math.min(MAX_TEST_CONCURRENCY, targets.size()));
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<CredentialHealthResult> results = new ArrayList<>(targets.size());
        try {
            List<Callable<CredentialHealthResult>> tasks = new ArrayList<>(targets.size());
            for (DeviceProviderCredential credential : targets) {
                tasks.add(() -> probe(credential));
            }
            List<Future<CredentialHealthResult>> futures =
                    executor.invokeAll(tasks, TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            for (int i = 0; i < futures.size(); i++) {
                DeviceProviderCredential credential = targets.get(i);
                CredentialHealthResult result = waitFor(credential, futures.get(i));
                results.add(result);
                credentialService.recordTestResult(credential.getDeviceCredentialId(), result.ok());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("批量连通性检测被中断", ex);
        } finally {
            executor.shutdownNow();
        }
        return ReturnResult.ok(results);
    }

    /**
     * 探测一套凭据，异常统一收敛成失败结果，避免单条异常拖垮整批
     *
     * @param credential 凭据
     * @return 探测结果
     */
    private CredentialHealthResult probe(DeviceProviderCredential credential) {
        long start = System.currentTimeMillis();
        try {
            boolean ok = deviceService.testConnection(connectInfo(credential));
            return new CredentialHealthResult(
                    credential.getDeviceCredentialId(),
                    credential.getDeviceCredentialName(),
                    credential.getDeviceCredentialVendor(),
                    ok,
                    System.currentTimeMillis() - start,
                    ok ? null : "平台登录失败，请检查 AK/SK 与平台地址");
        } catch (RuntimeException ex) {
            log.warn("凭据连通性探测异常: id={}, name={}", credential.getDeviceCredentialId(),
                    credential.getDeviceCredentialName(), ex);
            return new CredentialHealthResult(
                    credential.getDeviceCredentialId(),
                    credential.getDeviceCredentialName(),
                    credential.getDeviceCredentialVendor(),
                    false,
                    System.currentTimeMillis() - start,
                    rootMessage(ex));
        }
    }

    /**
     * 取单个探测结果：超时/中断/执行异常都归一为失败结果
     *
     * @param credential 凭据
     * @param future     探测任务
     * @return 探测结果
     */
    private CredentialHealthResult waitFor(DeviceProviderCredential credential,
                                           Future<CredentialHealthResult> future) {
        if (future.isCancelled()) {
            return new CredentialHealthResult(
                    credential.getDeviceCredentialId(),
                    credential.getDeviceCredentialName(),
                    credential.getDeviceCredentialVendor(),
                    false,
                    TEST_TIMEOUT_SECONDS * 1000L,
                    "探测超时（" + TEST_TIMEOUT_SECONDS + " 秒）");
        }
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new CredentialHealthResult(
                    credential.getDeviceCredentialId(),
                    credential.getDeviceCredentialName(),
                    credential.getDeviceCredentialVendor(),
                    false,
                    0L,
                    "探测被中断");
        } catch (Exception ex) {
            return new CredentialHealthResult(
                    credential.getDeviceCredentialId(),
                    credential.getDeviceCredentialName(),
                    credential.getDeviceCredentialVendor(),
                    false,
                    0L,
                    rootMessage(ex));
        }
    }

    /**
     * 取最内层异常信息，作为失败原因返回前端
     *
     * @param ex 异常
     * @return 非空描述
     */
    private static String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    /**
     * 由凭据构造建连参数（单条测试与批量检测共用）
     *
     * @param credential 凭据
     * @return 建连参数
     */
    private static DeviceConnectInfo connectInfo(DeviceProviderCredential credential) {
        return new DeviceConnectInfo(
                credential.getDeviceCredentialVendor(),
                "PLATFORM",
                null,
                0,
                null,
                null,
                credential.getDeviceCredentialPlatformType(),
                credential.getDeviceCredentialAppKey(),
                credential.getDeviceCredentialAppSecret(),
                null,
                null);
    }

    /**
     * 按凭据同步设备目录（拉取方向；落库 device_info）
     *
     * <p>以凭据 ID 为维度建连，只拉取该凭据名下设备目录，按 vendor + 厂商侧标识
     * 幂等 upsert；设备台账行回写 {@code deviceInfoCredentialId}。</p>
     *
     * @param id 凭据 ID
     * @return created / updated / skipped 统计
     */
    @PostMapping("/{id}/sync/devices")
    public ReturnResult<Map<String, Integer>> syncDevices(@PathVariable String id) {
        return ReturnResult.ok(deviceSyncService.syncDevicesByCredential(id));
    }

    /**
     * 按凭据同步组织树（拉取方向；落库 device_organization）
     *
     * @param id 凭据 ID
     * @return created / updated / skipped 统计
     */
    @PostMapping("/{id}/sync/org")
    public ReturnResult<Map<String, Integer>> syncOrg(@PathVariable String id) {
        return ReturnResult.ok(deviceSyncService.syncOrganizationsByCredential(id));
    }

    /**
     * 按凭据同步人员列表（拉取方向；落库 device_person）
     *
     * <p>人员与组织表解耦，单建 {@code device_person}（按 vendor + 厂商侧人员标识
     * 幂等入库），避免人员 API 结构换表时耦合到 org-person 关联表。</p>
     *
     * @param id 凭据 ID
     * @return created / updated / skipped 统计
     */
    @PostMapping("/{id}/sync/persons")
    public ReturnResult<Map<String, Integer>> syncPersons(@PathVariable String id) {
        return ReturnResult.ok(deviceSyncService.syncPersonsByCredential(id));
    }

    /**
     * 按凭据同步设备目录 + 组织树 + 人员列表（全量）
     *
     * @param id 凭据 ID
     * @return 各方向的统计
     */
    @PostMapping("/{id}/sync")
    public ReturnResult<Map<String, Map<String, Integer>>> syncAll(@PathVariable String id) {
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();
        result.put("devices", deviceSyncService.syncDevicesByCredential(id));
        result.put("organizations", deviceSyncService.syncOrganizationsByCredential(id));
        result.put("persons", deviceSyncService.syncPersonsByCredential(id));
        return ReturnResult.ok(result);
    }
}