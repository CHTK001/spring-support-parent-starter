package com.chua.starter.datasourcehub.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.entity.MsDatasourceOperLog;
import com.chua.starter.datasourcehub.support.mapper.MsDatasourceOperLogMapper;
import com.chua.starter.datasourcehub.support.model.DatasourceOperLogVO;
import com.chua.starter.datasourcehub.support.service.DatasourceOperLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志服务实现。
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceOperLogServiceImpl implements DatasourceOperLogService {

    /**
     * 每页条数上限。
     *
     * <p>没有上限时前端一次传 {@code size=100000} 就能把整张日志表拖出来，
     * 而这是给运维看的时间线，不是批量导出接口。</p>
     */
    private static final int MAX_PAGE_SIZE = 200;

    /**
     * 单条明细长度上限（与表的 TEXT 列匹配，防止一条超长 SQL 把行撑爆）。
     */
    private static final int MAX_DETAIL = 2000;

    private final MsDatasourceOperLogMapper mapper;

    @Override
    public void record(MsDatasourceOperLog row) {
        if (row == null || row.getDatasourceId() == null) {
            return;
        }
        try {
            if (row.getDetail() != null && row.getDetail().length() > MAX_DETAIL) {
                row.setDetail(row.getDetail().substring(0, MAX_DETAIL));
            }
            if (row.getRequestUrl() != null && row.getRequestUrl().length() > 500) {
                row.setRequestUrl(row.getRequestUrl().substring(0, 500));
            }
            if (row.getCreateTime() == null) {
                row.setCreateTime(LocalDateTime.now());
            }
            mapper.insert(row);
        } catch (RuntimeException e) {
            // 留痕失败不影响主流程：用户的增删改已经成功了，
            // 此时因为一条日志写不进而报错，等于把「记日志」变成功能的单点。
            log.warn("[datasource-oper-log] 写操作日志失败 datasourceId={} reason={}",
                    row.getDatasourceId(), e.getMessage());
        }
    }

    @Override
    public ReturnResult<Page<DatasourceOperLogVO>> page(Long datasourceId, String action,
                                                        LocalDateTime from, LocalDateTime to,
                                                        Integer page, Integer size) {
        if (datasourceId == null) {
            return ReturnResult.error("缺少数据源 ID");
        }
        long current = page == null || page < 1 ? 1L : page;
        long perPage = size == null || size < 1 ? 20L : Math.min(size, MAX_PAGE_SIZE);

        LambdaQueryWrapper<MsDatasourceOperLog> wrapper = new LambdaQueryWrapper<MsDatasourceOperLog>()
                .eq(MsDatasourceOperLog::getDatasourceId, datasourceId)
                .ge(from != null, MsDatasourceOperLog::getCreateTime, from)
                .le(to != null, MsDatasourceOperLog::getCreateTime, to)
                .eq(action != null && !action.isBlank(), MsDatasourceOperLog::getAction, action)
                // 最新在前：操作日志是「刚刚发生了什么」的时间线，倒序才不用翻页
                .orderByDesc(MsDatasourceOperLog::getCreateTime)
                .orderByDesc(MsDatasourceOperLog::getId);

        Page<MsDatasourceOperLog> rows = mapper.selectPage(new Page<>(current, perPage), wrapper);
        List<DatasourceOperLogVO> items = rows.getRecords().stream().map(this::toVO).toList();
        Page<DatasourceOperLogVO> result = new Page<>(rows.getCurrent(), rows.getSize(), rows.getTotal());
        result.setRecords(items);
        return ReturnResult.success(result);
    }

    @Override
    public void purge(Long datasourceId) {
        if (datasourceId == null) {
            return;
        }
        try {
            mapper.delete(new LambdaQueryWrapper<MsDatasourceOperLog>()
                    .eq(MsDatasourceOperLog::getDatasourceId, datasourceId));
        } catch (RuntimeException e) {
            log.warn("[datasource-oper-log] 清理数据源 {} 的日志失败 reason={}", datasourceId, e.getMessage());
        }
    }

    @Override
    public int purgeOlderThan(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }
        try {
            return mapper.delete(new LambdaQueryWrapper<MsDatasourceOperLog>()
                    .lt(MsDatasourceOperLog::getCreateTime, LocalDateTime.now().minusDays(retentionDays)));
        } catch (RuntimeException e) {
            log.warn("[datasource-oper-log] 清理过期日志失败 reason={}", e.getMessage());
            return 0;
        }
    }

    /**
     * 实体转 VO。
     *
     * @param row 实体
     * @return VO
     */
    private DatasourceOperLogVO toVO(MsDatasourceOperLog row) {
        return new DatasourceOperLogVO(
                row.getId(), row.getDatasourceId(), row.getDatasourceName(),
                row.getAction(), row.getActionName(), row.getTarget(), row.getDetail(),
                row.getSuccess(), row.getCostMs(), row.getOperator(), row.getClientIp(),
                row.getRequestUrl(), row.getCreateTime());
    }
}