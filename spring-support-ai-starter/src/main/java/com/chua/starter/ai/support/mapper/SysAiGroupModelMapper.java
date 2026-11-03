package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiGroupModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 分组-模型关联 映射器。
 *
 * @author CH
 * @since 4.0.0
 */
@Mapper
public interface SysAiGroupModelMapper extends BaseMapper<SysAiGroupModel> {

    /**
     * 取分组关联到的模型编码（{@code sys_ai_group_model} → {@code sys_ai_module}）。
     *
     * <p>关联表存的是模块 ID，不是模型编码；列表展示与鉴权要的是模型编码，所以在库里
     * 一次 JOIN 完，避免 Java 侧先查一批 ID 再逐个反查。</p>
     *
     * @param groupId 分组标识
     * @return 模型编码列表（可能含空行），无关联时为空列表
     */
    @Select("SELECT m.`sys_ai_module_model_id` FROM `sys_ai_group_model` gm "
            + "JOIN `sys_ai_module` m ON m.`sys_ai_module_id` = gm.`sys_ai_module_id` "
            + "WHERE gm.`sys_ai_group_id` = #{groupId} AND m.`sys_ai_module_model_id` IS NOT NULL")
    List<String> selectGroupModelCodes(@Param("groupId") Integer groupId);
}
