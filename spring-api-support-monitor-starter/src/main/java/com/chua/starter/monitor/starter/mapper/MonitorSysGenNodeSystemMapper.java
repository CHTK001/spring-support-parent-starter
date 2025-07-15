package com.chua.starter.monitor.starter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.starter.entity.MonitorSysGenNodeSystem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 节点系统表 Mapper 接口
 *
 * @author CH
 * @since 2024/12/30
 */
@Mapper
public interface MonitorSysGenNodeSystemMapper extends BaseMapper<MonitorSysGenNodeSystem> {

    /**
     * 根据IP和端口查询节点
     */
    @Select("SELECT * FROM monitor_sys_gen_node_system WHERE node_system_ip_address = #{ipAddress} AND node_system_port = #{port}")
    MonitorSysGenNodeSystem selectByIpAndPort(@Param("ipAddress") String ipAddress, @Param("port") Integer port);

    /**
     * 更新节点在线状态
     */
    @Update("UPDATE monitor_sys_gen_node_system SET " +
            "node_system_status = #{status}, " +
            "node_system_last_heartbeat_time = #{heartbeatTime}, " +
            "node_system_last_online_time = CASE WHEN #{status} = 'ONLINE' THEN #{heartbeatTime} ELSE node_system_last_online_time END, " +
            "node_system_last_offline_time = CASE WHEN #{status} != 'ONLINE' THEN #{heartbeatTime} ELSE node_system_last_offline_time END, " +
            "node_system_healthy = #{healthy}, " +
            "update_time = #{heartbeatTime} " +
            "WHERE node_system_id = #{nodeId}")
    int updateNodeStatus(@Param("nodeId") String nodeId, 
                         @Param("status") String status, 
                         @Param("healthy") Boolean healthy,
                         @Param("heartbeatTime") LocalDateTime heartbeatTime);

    /**
     * 更新节点性能指标
     */
    @Update("UPDATE monitor_sys_gen_node_system SET " +
            "node_system_avg_cpu_usage = #{cpuUsage}, " +
            "node_system_avg_memory_usage = #{memoryUsage}, " +
            "node_system_avg_disk_usage = #{diskUsage}, " +
            "node_system_avg_network_latency = #{networkLatency}, " +
            "node_system_avg_response_time = #{responseTime}, " +
            "update_time = NOW() " +
            "WHERE node_system_id = #{nodeId}")
    int updateNodeMetrics(@Param("nodeId") String nodeId,
                          @Param("cpuUsage") Double cpuUsage,
                          @Param("memoryUsage") Double memoryUsage,
                          @Param("diskUsage") Double diskUsage,
                          @Param("networkLatency") Double networkLatency,
                          @Param("responseTime") Double responseTime);

    /**
     * 增加连接次数
     */
    @Update("UPDATE monitor_sys_gen_node_system SET " +
            "node_system_connection_count = COALESCE(node_system_connection_count, 0) + 1, " +
            "update_time = NOW() " +
            "WHERE node_system_id = #{nodeId}")
    int incrementConnectionCount(@Param("nodeId") String nodeId);

    /**
     * 增加错误次数
     */
    @Update("UPDATE monitor_sys_gen_node_system SET " +
            "node_system_error_count = COALESCE(node_system_error_count, 0) + 1, " +
            "node_system_last_error_time = #{errorTime}, " +
            "node_system_last_error_message = #{errorMessage}, " +
            "update_time = #{errorTime} " +
            "WHERE node_system_id = #{nodeId}")
    int incrementErrorCount(@Param("nodeId") String nodeId, 
                           @Param("errorTime") LocalDateTime errorTime,
                           @Param("errorMessage") String errorMessage);

    /**
     * 获取节点统计信息
     */
    @Select("SELECT " +
            "COUNT(*) as totalNodes, " +
            "SUM(CASE WHEN node_system_status = 'ONLINE' THEN 1 ELSE 0 END) as onlineNodes, " +
            "SUM(CASE WHEN node_system_status = 'OFFLINE' THEN 1 ELSE 0 END) as offlineNodes, " +
            "SUM(CASE WHEN node_system_healthy = 1 THEN 1 ELSE 0 END) as healthyNodes, " +
            "SUM(CASE WHEN node_system_status = 'ERROR' THEN 1 ELSE 0 END) as errorNodes, " +
            "SUM(CASE WHEN node_system_status = 'MAINTENANCE' THEN 1 ELSE 0 END) as maintenanceNodes, " +
            "SUM(COALESCE(node_system_connection_count, 0)) as totalConnections, " +
            "AVG(COALESCE(node_system_avg_response_time, 0)) as averageResponseTime, " +
            "AVG(COALESCE(node_system_avg_cpu_usage, 0)) as averageCpuUsage, " +
            "AVG(COALESCE(node_system_avg_memory_usage, 0)) as averageMemoryUsage, " +
            "AVG(COALESCE(node_system_avg_disk_usage, 0)) as averageDiskUsage " +
            "FROM monitor_sys_gen_node_system")
    Map<String, Object> getNodeStatistics();

    /**
     * 按应用名称分组统计节点数量
     */
    @Select("SELECT node_system_application_name as applicationName, COUNT(*) as nodeCount " +
            "FROM monitor_sys_gen_node_system " +
            "GROUP BY node_system_application_name")
    List<Map<String, Object>> getNodeCountByApplication();

    /**
     * 按状态分组统计节点数量
     */
    @Select("SELECT node_system_status as status, COUNT(*) as nodeCount " +
            "FROM monitor_sys_gen_node_system " +
            "GROUP BY node_system_status")
    List<Map<String, Object>> getNodeCountByStatus();

    /**
     * 按节点类型分组统计节点数量
     */
    @Select("SELECT node_system_type as nodeType, COUNT(*) as nodeCount " +
            "FROM monitor_sys_gen_node_system " +
            "WHERE node_system_type IS NOT NULL " +
            "GROUP BY node_system_type")
    List<Map<String, Object>> getNodeCountByType();

    /**
     * 获取最近活跃的节点（按最后心跳时间排序）
     */
    @Select("SELECT * FROM monitor_sys_gen_node_system " +
            "WHERE node_system_status = 'ONLINE' " +
            "ORDER BY node_system_last_heartbeat_time DESC " +
            "LIMIT #{limit}")
    List<MonitorSysGenNodeSystem> getRecentActiveNodes(@Param("limit") int limit);

    /**
     * 获取响应最快的节点
     */
    @Select("SELECT * FROM monitor_sys_gen_node_system " +
            "WHERE node_system_status = 'ONLINE' AND node_system_avg_response_time IS NOT NULL " +
            "ORDER BY node_system_avg_response_time ASC " +
            "LIMIT #{limit}")
    List<MonitorSysGenNodeSystem> getFastestNodes(@Param("limit") int limit);

    /**
     * 获取负载最高的节点
     */
    @Select("SELECT * FROM monitor_sys_gen_node_system " +
            "WHERE node_system_status = 'ONLINE' AND node_system_avg_cpu_usage IS NOT NULL " +
            "ORDER BY node_system_avg_cpu_usage DESC " +
            "LIMIT #{limit}")
    List<MonitorSysGenNodeSystem> getHighestLoadNodes(@Param("limit") int limit);

    /**
     * 搜索节点
     */
    @Select("<script>" +
            "SELECT * FROM monitor_sys_gen_node_system " +
            "WHERE 1=1 " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (node_system_name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR node_system_application_name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR node_system_ip_address LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "<if test='status != null and status != \"\"'>" +
            "AND node_system_status = #{status} " +
            "</if>" +
            "<if test='applicationName != null and applicationName != \"\"'>" +
            "AND node_system_application_name = #{applicationName} " +
            "</if>" +
            "ORDER BY node_system_last_heartbeat_time DESC" +
            "</script>")
    List<MonitorSysGenNodeSystem> searchNodes(@Param("keyword") String keyword,
                                              @Param("status") String status,
                                              @Param("applicationName") String applicationName);

    /**
     * 批量更新节点状态为离线
     */
    @Update("UPDATE monitor_sys_gen_node_system SET " +
            "node_system_status = 'OFFLINE', " +
            "node_system_last_offline_time = NOW(), " +
            "node_system_healthy = 0, " +
            "update_time = NOW() " +
            "WHERE node_system_id IN " +
            "<foreach collection='nodeIds' item='nodeId' open='(' separator=',' close=')'>" +
            "#{nodeId}" +
            "</foreach>")
    int batchUpdateOfflineStatus(@Param("nodeIds") List<String> nodeIds);

    /**
     * 更新在线时长统计
     */
    @Update("UPDATE monitor_sys_gen_node_system SET " +
            "node_system_total_online_duration = COALESCE(node_system_total_online_duration, 0) + #{duration}, " +
            "update_time = NOW() " +
            "WHERE node_system_id = #{nodeId}")
    int updateOnlineDuration(@Param("nodeId") String nodeId, @Param("duration") Long duration);
}
