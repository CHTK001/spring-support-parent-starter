// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsServerOperLog;
import com.chua.starter.monitor.support.mapper.MsServerOperLogMapper;
import com.chua.starter.monitor.support.server.model.ServerOperLogVO;
import com.chua.starter.monitor.support.service.ServerOperLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务管理节点操作日志服务实现。
 *
 * @author CH
 * @since 4.0.0.55
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServerOperLogServiceImpl implements ServerOperLogService {

    /**
     * 每页条数上限。
     *
     * <p>没有上限时前端一次传 {@code size=100000} 就能把整张日志表拖出来，
     * 而这是给运维看的时间线，不是批量导出接口。</p>
     */
    private static final int MAX_PAGE_SIZE = 200;

    /**
     * 单条明细长度上限（与表的 TEXT 列匹配，防止一条超长 JSON 把行撑爆）。
     */
    private static final int MAX_DETAIL = 2000;

    private final MsServerOperLogMapper mapper;

    @Override
    public void record(MsServerOperLog row) {
        if (row == null || row.getNodeId() == null) {
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
            log.warn("[server-oper-log] 写操作日志失败 nodeId={} reason={}",
                    row.getNodeId(), e.getMessage());
        }
    }

    @Override
    public ReturnResult<Page<ServerOperLogVO>> page(Long nodeId, String action,
                                                    LocalDateTime from, LocalDateTime to,
                                                    Integer page, Integer size) {
        if (nodeId == null) {
            return ReturnResult.error("缺少节点 ID");
        }
        long current = page == null || page < 1 ? 1L : page;
        long perPage = size == null || size < 1 ? 20L : Math.min(size, MAX_PAGE_SIZE);

        LambdaQueryWrapper<MsServerOperLog> wrapper = new LambdaQueryWrapper<MsServerOperLog>()
                .eq(MsServerOperLog::getNodeId, nodeId)
                .ge(from != null, MsServerOperLog::getCreateTime, from)
                .le(to != null, MsServerOperLog::getCreateTime, to)
                .eq(action != null && !action.isBlank(), MsServerOperLog::getAction, action)
                // 最新在前：操作日志是「刚刚发生了什么」的时间线，倒序才不用翻页
                .orderByDesc(MsServerOperLog::getCreateTime)
                .orderByDesc(MsServerOperLog::getId);

        Page<MsServerOperLog> rows = mapper.selectPage(new Page<>(current, perPage), wrapper);
        List<ServerOperLogVO> items = rows.getRecords().stream().map(this::toVO).toList();
        Page<ServerOperLogVO> result = new Page<>(rows.getCurrent(), rows.getSize(), rows.getTotal());
        result.setRecords(items);
        return ReturnResult.success(result);
    }

    @Override
    public void purge(Long nodeId) {
        if (nodeId == null) {
            return;
        }
        try {
            mapper.delete(new LambdaQueryWrapper<MsServerOperLog>()
                    .eq(MsServerOperLog::getNodeId, nodeId));
        } catch (RuntimeException e) {
            log.warn("[server-oper-log] 清理节点 {} 的日志失败 reason={}", nodeId, e.getMessage());
        }
    }

    @Override
    public int purgeOlderThan(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }
        try {
            return mapper.delete(new LambdaQueryWrapper<MsServerOperLog>()
                    .lt(MsServerOperLog::getCreateTime, LocalDateTime.now().minusDays(retentionDays)));
        } catch (RuntimeException e) {
            log.warn("[server-oper-log] 清理过期日志失败 reason={}", e.getMessage());
            return 0;
        }
    }

    /**
     * 实体转 VO。
     *
     * @param row 实体
     * @return VO
     */
    private ServerOperLogVO toVO(MsServerOperLog row) {
        return new ServerOperLogVO(
                row.getId(), row.getNodeId(), row.getNodeName(),
                row.getAction(), row.getActionName(), row.getTarget(), row.getDetail(),
                row.getSuccess(), row.getCostMs(), row.getOperator(), row.getClientIp(),
                row.getRequestUrl(), row.getCreateTime());
    }
}
