package com.chua.starter.monitor.starter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.starter.entity.MonitorSysGenScriptCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 脚本分类Mapper
 * @author CH
 * @since 2024/12/19
 */
@Mapper
public interface MonitorSysGenScriptCategoryMapper extends BaseMapper<MonitorSysGenScriptCategory> {

    /**
     * 根据分类名称查找分类
     */
    @Select("SELECT * FROM monitor_sys_gen_script_category WHERE monitor_sys_gen_script_category_name = #{categoryName}")
    MonitorSysGenScriptCategory findByCategoryName(@Param("categoryName") String categoryName);

    /**
     * 查找所有启用的分类
     */
    @Select("SELECT * FROM monitor_sys_gen_script_category " +
            "WHERE monitor_sys_gen_script_category_status = 1 " +
            "ORDER BY monitor_sys_gen_script_category_sort ASC, monitor_sys_gen_script_category_name ASC")
    List<MonitorSysGenScriptCategory> findEnabledCategories();

    /**
     * 查找根分类
     */
    @Select("SELECT * FROM monitor_sys_gen_script_category " +
            "WHERE (monitor_sys_gen_script_category_parent_id IS NULL OR monitor_sys_gen_script_category_parent_id = 0) " +
            "AND monitor_sys_gen_script_category_status = 1 " +
            "ORDER BY monitor_sys_gen_script_category_sort ASC")
    List<MonitorSysGenScriptCategory> findRootCategories();

    /**
     * 根据父分类ID查找子分类
     */
    @Select("SELECT * FROM monitor_sys_gen_script_category " +
            "WHERE monitor_sys_gen_script_category_parent_id = #{parentId} " +
            "AND monitor_sys_gen_script_category_status = 1 " +
            "ORDER BY monitor_sys_gen_script_category_sort ASC")
    List<MonitorSysGenScriptCategory> findByParentId(@Param("parentId") Integer parentId);

    /**
     * 获取分类树结构
     */
    @Select("SELECT * FROM monitor_sys_gen_script_category " +
            "WHERE monitor_sys_gen_script_category_status = 1 " +
            "ORDER BY monitor_sys_gen_script_category_parent_id ASC, monitor_sys_gen_script_category_sort ASC")
    List<MonitorSysGenScriptCategory> findCategoryTree();

    /**
     * 检查分类名称是否存在
     */
    @Select("SELECT COUNT(*) FROM monitor_sys_gen_script_category " +
            "WHERE monitor_sys_gen_script_category_name = #{categoryName} " +
            "AND monitor_sys_gen_script_category_id != #{excludeId}")
    int countByCategoryName(@Param("categoryName") String categoryName, @Param("excludeId") Integer excludeId);

    /**
     * 获取分类下的脚本数量
     */
    @Select("SELECT COUNT(*) FROM monitor_sys_gen_script " +
            "WHERE monitor_sys_gen_script_category = #{categoryName} " +
            "AND monitor_sys_gen_script_status = 1")
    int countScriptsByCategory(@Param("categoryName") String categoryName);
}
