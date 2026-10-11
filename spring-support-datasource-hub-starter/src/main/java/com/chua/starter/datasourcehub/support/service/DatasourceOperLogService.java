package com.chua.starter.datasourcehub.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.entity.MsDatasourceOperLog;
import com.chua.starter.datasourcehub.support.model.DatasourceOperLogVO;

import java.time.LocalDateTime;

/**
 * 数据源中心操作日志服务。
 *
 * <p>写入侧只有一条入口 {@link #record}，由 {@code DatasourceOperLogListener}
 * 从 {@code @SysLog} 切面发出的事件驱动；读取侧是按数据源分页查询。
 * 这样「谁写、什么时候写」只有一处定义：日志内容由切面采集，本服务只落库与查询，
 * 不在各处散落手写埋点（散开的埋点必然出现「这个接口忘了记」）。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public interface DatasourceOperLogService {

    /**
     * 记一条操作日志。
     *
     * <p>写失败只记 warn 不上抛：操作日志是留痕功能，
     * 它的失败不能把用户的正常操作变成一次报错。</p>
     *
     * @param log 日志行
     */
    void record(MsDatasourceOperLog log);

    /**
     * 按数据源分页查询操作日志。
     *
     * @param datasourceId 数据源 ID
     * @param action       操作码过滤，空则不过滤
     * @param from         起始时间，空则不过滤
     * @param to           截止时间，空则不过滤
     * @param page         页码，从 1 开始
     * @param size         每页条数
     * @return 分页结果
     */
    ReturnResult<Page<DatasourceOperLogVO>> page(Long datasourceId, String action,
                                                 LocalDateTime from, LocalDateTime to,
                                                 Integer page, Integer size);

    /**
     * 删除某个数据源的全部操作日志（数据源被删除时调用）。
     *
     * <p>{@code datasource_id} 是非空列且没有外键级联，不显式清就会留下
     * 一批指向已不存在数据源的孤儿行 —— 而按 id 查列表仍会把它们查出来。</p>
     *
     * @param datasourceId 数据源 ID
     */
    void purge(Long datasourceId);

    /**
     * 清理超保留期的操作日志。
     *
     * @param retentionDays 保留天数；小于等于 0 表示不清理
     * @return 删除行数
     */
    int purgeOlderThan(int retentionDays);
}