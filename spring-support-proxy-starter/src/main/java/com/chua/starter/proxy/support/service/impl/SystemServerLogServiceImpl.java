package com.chua.starter.proxy.support.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.starter.proxy.support.entity.SystemServerLog;
import com.chua.starter.proxy.support.mapper.SystemServerLogMapper;
import com.chua.starter.proxy.support.service.server.SystemServerLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 系统服务器日志服务实现类
 *
 * @author CH
 * @since 2023-04-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemServerLogServiceImpl extends ServiceImpl<SystemServerLogMapper, SystemServerLog> implements SystemServerLogService {

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, Math.max(4, Runtime.getRuntime().availableProcessors()),
            60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(10000),
            r -> new Thread(r, "systemserverLog-recorder"),
            new ThreadPoolExecutor.DiscardPolicy()
    );

    /**
     * 分页查询系统服务器日志
     *
     * @param page         分页参数，例如：new Page<>(1, 10)
     * @param serverId     服务器ID，用于筛选特定服务器的日志，例如：1
     * @param filterType   过滤器类型，例如："com.example.MyFilter"
     * @param processStatus 处理状态，例如："BLACKLIST_BLOCK"
     * @param clientIp     客户端IP地址，例如："192.168.1.100"
     * @param startTime    查询开始时间，例如：LocalDateTime.of(2023, 1, 1, 0, 0, 0)
     * @param endTime      查询结束时间，例如：LocalDateTime.now()
     * @return 分页查询结果
     */
    @Override
    public ReturnResult<IPage<SystemServerLog>> pageLogs(Page<SystemServerLog> page, Integer serverId, String filterType, String processStatus, String clientIp, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            IPage<SystemServerLog> records = baseMapper.pageLogs(page, serverId, filterType, processStatus, clientIp, startTime, endTime);
            return ReturnResult.success(records);
        } catch (Exception e) {
            log.error("分页查询systemserverLog失败", e);
            return ReturnResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 导出系统服务器日志为CSV格式
     *
     * @param serverId     服务器ID，用于筛选特定服务器的日志，例如：1
     * @param filterType   过滤器类型，例如："com.example.MyFilter"
     * @param processStatus 处理状态，例如："BLACKLIST_BLOCK"
     * @param clientIp     客户端IP地址，例如："192.168.1.100"
     * @param startTime    查询开始时间，例如：LocalDateTime.of(2023, 1, 1, 0, 0, 0)
     * @param endTime      查询结束时间，例如：LocalDateTime.now()
     * @return CSV格式的字节数组
     */
    @Override
    public ReturnResult<byte[]> exportCsv(Integer serverId, String filterType, String processStatus, String clientIp, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            List<SystemServerLog> list = baseMapper.selectForExport(serverId, filterType, processStatus, clientIp, startTime, endTime);
            String header = "store_time,access_time,server_id,filter_type,process_status,client_ip,client_geo,duration_ms\n";
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String body = list.stream().map(it -> String.join(",",
                    fmtSafe(it.getStoreTime(), fmt),
                    fmtSafe(it.getAccessTime(), fmt),
                    safe(String.valueOf(it.getServerId() == null ? "" : it.getServerId())),
                    safe(it.getFilterType()),
                    safe(it.getProcessStatus()),
                    safe(it.getClientIp()),
                    safe(it.getClientGeo()),
                    safe(String.valueOf(it.getDurationMs() == null ? "" : it.getDurationMs()))
            )).collect(Collectors.joining("\n"));
            byte[] bytes = (header + body).getBytes(StandardCharsets.UTF_8);
            return ReturnResult.success(bytes);
        } catch (Exception e) {
            log.error("导出systemserverLog失败", e);
            return ReturnResult.error("导出失败: " + e.getMessage());
        }
    }

    /**
     * 清理指定时间之前的系统服务器日志
     *
     * @param beforeTime 清理时间点，该时间点之前的数据将被删除，例如：LocalDateTime.of(2023, 1, 1, 0, 0, 0)
     * @return 删除的记录数
     */
    @Override
    public ReturnResult<Integer> cleanup(LocalDateTime beforeTime) {
        try {
            int cnt = baseMapper.deleteBefore(beforeTime);
            return ReturnResult.success(cnt);
        } catch (Exception e) {
            log.error("清理systemserverLog失败", e);
            return ReturnResult.error("清理失败: " + e.getMessage());
        }
    }

    /**
     * 异步记录系统服务器日志
     *
     * @param systemServerLog 系统服务器日志实体对象，例如：new SystemServerLog()
     */
    @Override
    public void asyncRecord(SystemServerLog systemServerLog) {
        if (null == systemServerLog) return;
        executor.execute(() -> {
            try {
                // 兜底填充
                if (systemServerLog.getStoreTime() == null) {
                    systemServerLog.setStoreTime(LocalDateTime.now());
                }
                save(systemServerLog);
            } catch (Exception e) {
                log.error("保存systemserverLog失败", e);
            }
        });
    }

    private String fmtSafe(LocalDateTime time, DateTimeFormatter fmt) {
        return time == null ? "" : fmt.format(time);
    }

    private String safe(String v) {
        return StringUtils.isEmpty(v) ? "" : v.replaceAll(",", " ");
    }
}





