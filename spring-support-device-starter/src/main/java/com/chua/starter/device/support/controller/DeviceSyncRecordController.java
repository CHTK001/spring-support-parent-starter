package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.support.entity.DeviceSyncOutbox;
import com.chua.starter.device.support.entity.DeviceSyncRecord;
import com.chua.starter.device.support.model.DeviceSyncOutboxRetryBatchRequest;
import com.chua.starter.device.support.model.DeviceSyncOutboxRetryResult;
import com.chua.starter.device.support.model.SyncOutboxQuery;
import com.chua.starter.device.support.model.SyncRecordQuery;
import com.chua.starter.device.support.service.DeviceSyncRecordService;
import com.chua.starter.mybatis.entity.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 同步记录查询接口（供「同步记录」视图使用）。
 *
 * <p>同步执行记录：每次厂商目录拉取 / 本地写回一行，含计数、状态与失败原因；</p>
 * <p>推送队列（outbox）：写回失败的设备待重试清单，支持单条重试。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@RestController
@RequestMapping("/api/device/sync-records")
@RequiredArgsConstructor
public class DeviceSyncRecordController {

    /**
     * 同步记录查询服务
     */
    private final DeviceSyncRecordService syncRecordService;

    /**
     * 分页查询同步执行记录。
     *
     * @param query 筛选条件（vendor / mode / phase / status / start / end）
     * @return 分页结果，按开始时间倒序
     */
    @GetMapping("/page")
    public PageResult<DeviceSyncRecord> pageRecords(SyncRecordQuery query) {
        return syncRecordService.pageRecords(query);
    }

    /**
     * 分页查询推送队列（outbox）。
     *
     * @param query 筛选条件（vendor / action / status / deviceId）
     * @return 分页结果，按创建时间倒序
     */
    @GetMapping("/outbox/page")
    public PageResult<DeviceSyncOutbox> pageOutbox(SyncOutboxQuery query) {
        return syncRecordService.pageOutbox(query);
    }

    /**
     * 概览计数：同步记录按状态、推送队列按状态。
     *
     * @return records / outbox 两个维度的状态计数
     */
    @GetMapping("/summary")
    public ReturnResult<Map<String, Object>> summary() {
        return ReturnResult.ok(syncRecordService.summary());
    }

    /**
     * 批量查询各凭据最近一条同步记录（凭据表「最近同步」列）。
     *
     * @param credentialIds 凭据 ID 清单（逗号分隔）
     * @return {credentialId: 最近一条同步记录}；没有同步记录的凭据不在结果里
     */
    @GetMapping("/latest")
    public ReturnResult<Map<String, DeviceSyncRecord>> latestByCredentials(
            @RequestParam("credentialIds") List<String> credentialIds) {
        return ReturnResult.ok(syncRecordService.latestByCredentialIds(credentialIds));
    }

    /**
     * 重试一条推送队列记录：重新写回厂商，成功置 sent，失败按退避推迟下次重试。
     *
     * @param id 队列记录 ID
     * @return 更新后的队列记录
     */
    @PostMapping("/outbox/{id}/retry")
    public ReturnResult<DeviceSyncOutbox> retryOutbox(@PathVariable String id) {
        return ReturnResult.ok(syncRecordService.retryOutbox(id));
    }

    /**
     * 批量重试推送队列记录：逐条写回厂商，返回每条的执行结果。
     *
     * <p>单条失败不影响其余记录；单次上限 100 条，超出直接拒绝。</p>
     *
     * @param request 待重试的队列记录 ID 清单
     * @return 每条记录的重试结果，顺序与入参一致
     */
    @PostMapping("/outbox/retry-batch")
    public ReturnResult<List<DeviceSyncOutboxRetryResult>> retryOutboxBatch(
            @RequestBody DeviceSyncOutboxRetryBatchRequest request) {
        return ReturnResult.ok(syncRecordService.retryOutboxBatch(request));
    }
}
