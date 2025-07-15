package com.chua.starter.monitor.starter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.starter.entity.MonitorSysGenScript;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 脚本管理Mapper
 * @author CH
 * @since 2024/12/19
 */
@Mapper
public interface MonitorSysGenScriptMapper extends BaseMapper<MonitorSysGenScript> {

    /**
     * 根据脚本名称查找脚本
     */
    @Select("SELECT * FROM monitor_sys_gen_script WHERE monitor_sys_gen_script_name = #{scriptName}")
    MonitorSysGenScript findByScriptName(@Param("scriptName") String scriptName);

    /**
     * 根据脚本类型查找脚本
     */
    @Select("SELECT * FROM monitor_sys_gen_script WHERE monitor_sys_gen_script_type = #{scriptType} AND monitor_sys_gen_script_status = 1")
    List<MonitorSysGenScript> findByScriptType(@Param("scriptType") String scriptType);

    /**
     * 根据分类查找脚本
     */
    @Select("SELECT * FROM monitor_sys_gen_script WHERE monitor_sys_gen_script_category = #{category} AND monitor_sys_gen_script_status = 1")
    List<MonitorSysGenScript> findByCategory(@Param("category") String category);

    /**
     * 根据标签查找脚本
     */
    @Select("SELECT * FROM monitor_sys_gen_script WHERE monitor_sys_gen_script_tags LIKE CONCAT('%', #{tag}, '%') AND monitor_sys_gen_script_status = 1")
    List<MonitorSysGenScript> findByTag(@Param("tag") String tag);

    /**
     * 更新脚本执行统计
     */
    @Update("UPDATE monitor_sys_gen_script SET " +
            "monitor_sys_gen_script_execute_count = monitor_sys_gen_script_execute_count + 1, " +
            "monitor_sys_gen_script_last_execute_time = #{executeTime} " +
            "WHERE monitor_sys_gen_script_id = #{scriptId}")
    int updateExecuteStatistics(@Param("scriptId") Integer scriptId, @Param("executeTime") LocalDateTime executeTime);

    /**
     * 获取脚本统计信息
     */
    @Select("SELECT " +
            "COUNT(*) as totalCount, " +
            "SUM(CASE WHEN monitor_sys_gen_script_status = 1 THEN 1 ELSE 0 END) as enabledCount, " +
            "SUM(CASE WHEN monitor_sys_gen_script_status = 0 THEN 1 ELSE 0 END) as disabledCount, " +
            "SUM(monitor_sys_gen_script_execute_count) as totalExecutions " +
            "FROM monitor_sys_gen_script")
    ScriptStatistics getScriptStatistics();

    /**
     * 获取各类型脚本数量统计
     */
    @Select("SELECT monitor_sys_gen_script_type as scriptType, COUNT(*) as count " +
            "FROM monitor_sys_gen_script " +
            "WHERE monitor_sys_gen_script_status = 1 " +
            "GROUP BY monitor_sys_gen_script_type " +
            "ORDER BY count DESC")
    List<ScriptTypeCount> getScriptTypeStatistics();

    /**
     * 获取各分类脚本数量统计
     */
    @Select("SELECT monitor_sys_gen_script_category as category, COUNT(*) as count " +
            "FROM monitor_sys_gen_script " +
            "WHERE monitor_sys_gen_script_status = 1 " +
            "GROUP BY monitor_sys_gen_script_category " +
            "ORDER BY count DESC")
    List<ScriptCategoryCount> getScriptCategoryStatistics();

    /**
     * 脚本统计信息
     */
    interface ScriptStatistics {
        Long getTotalCount();
        Long getEnabledCount();
        Long getDisabledCount();
        Long getTotalExecutions();
    }

    /**
     * 脚本类型统计
     */
    interface ScriptTypeCount {
        String getScriptType();
        Long getCount();
    }

    /**
     * 脚本分类统计
     */
    interface ScriptCategoryCount {
        String getCategory();
        Long getCount();
    }
}
