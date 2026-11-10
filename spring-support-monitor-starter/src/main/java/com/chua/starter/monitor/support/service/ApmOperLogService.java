package com.chua.starter.monitor.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsApmOperLog;
import com.chua.starter.monitor.support.server.model.ApmOperLogVO;

import java.time.LocalDateTime;

/**
 * APM 操作日志服务。
 *
 * <p>写入侧只有一条入口 {@link #record}，由 {@code ApmOperLogSink} 的实现调用；
 * 读取侧是全表分页查询（APM 只有一套设置，没有「按对象过滤」的主语）。
 * 写失败只记 warn 不上抛：留痕不能把「改设置」这件事本身弄失败。</p>
 *
 * @author CH
 * @since 4.0.0.56
 */
public interface ApmOperLogService {

    /**
     * 记一条 APM 操作日志。
     *
     * @param log 日志行
     */
    void record(MsApmOperLog log);

    /**
     * 分页查询 APM 操作日志。
     *
     * @param action 操作码过滤，空则不过滤
     * @param from   起始时间，空则不过滤
     * @param to     截止时间，空则不过滤
     * @param page   页码，从 1 开始
     * @param size   每页条数
     * @return 分页结果
     */
    ReturnResult<Page<ApmOperLogVO>> page(String action,
                                         LocalDateTime from, LocalDateTime to,
                                         Integer page, Integer size);

    /**
     * 清理超保留期的操作日志。
     *
     * @param retentionDays 保留天数；小于等于 0 表示不清理
     * @return 删除行数
     */
    int purgeOlderThan(int retentionDays);
}
