package com.chua.starter.sync.data.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.sync.data.support.entity.MonitorSyncAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 同步告警 Mapper 接口
 *
 * @author System
 * @since 2026/03/09
 */
@Mapper
public interface MonitorSyncAlertMapper extends BaseMapper<MonitorSyncAlert> {

    /**
     * 查询未解决的告警
     *
     * @param syncTaskId 任务ID（可选）
     * @return 未解决的告警列表
     */
    @Select("<script>" +
            "SELECT * FROM monitor_sync_alert WHERE is_resolved = 0 " +
            "<if test='syncTaskId != null'> AND sync_task_id = #{syncTaskId} </if>" +
            "ORDER BY alert_time DESC" +
            "</script>")
    List<MonitorSyncAlert> selectUnresolvedAlerts(@Param("syncTaskId") Long syncTaskId);

    /**
     * 标记告警为已解决
     *
     * @param alertId 告警ID
     * @return 更新行数
     */
    @Update("UPDATE monitor_sync_alert SET is_resolved = 1, resolved_time = NOW() WHERE alert_id = #{alertId}")
    int resolveAlert(@Param("alertId") Long alertId);
}
