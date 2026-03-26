package com.chua.starter.proxy.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.proxy.support.entity.SystemServerLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * systemserverLog Mapper
 */
@Mapper
public interface SystemServerLogMapper extends BaseMapper<SystemServerLog> {

    IPage<SystemServerLog> pageLogs(
            Page<SystemServerLog> page,
            @Param("serverId") Integer serverId,
            @Param("filterType") String filterType,
            @Param("processStatus") String processStatus,
            @Param("clientIp") String clientIp,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    List<SystemServerLog> selectForExport(
            @Param("serverId") Integer serverId,
            @Param("filterType") String filterType,
            @Param("processStatus") String processStatus,
            @Param("clientIp") String clientIp,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    int deleteBefore(@Param("beforeTime") LocalDateTime beforeTime);
}





