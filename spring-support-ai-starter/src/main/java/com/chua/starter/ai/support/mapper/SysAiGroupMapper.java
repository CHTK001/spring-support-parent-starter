package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * AI 模型分组 映射器。
 *
 * @author CH
 * @since 4.0.0
 */
@Mapper
public interface SysAiGroupMapper extends BaseMapper<SysAiGroup> {

    /**
     * 按条件查询分组列表（名称模糊搜索、状态过滤、排序）。
     *
     * @param params 查询参数（可包含 sysAI群体名称、sysAI群体状态）
     * @return 分组列表
     */
    List<Map<String, Object>> group(Map<String, Object> params);

    /**
     * 读取分组配置的模型清单原串（{@code sys_ai_group.sys_ai_group_models}，JSON 数组）。
     *
     * <p>{@link SysAiGroup} 实体只映射分组的元信息列，没有这条业务列；而「某个 appKey
     * 能用哪些模型」完全取决于它所属分组的这份清单，鉴权与 {@code /v1/models} 都靠它，
     * 所以单独取一次原串，再由调用方解析成集合。</p>
     *
     * @param groupId 分组标识
     * @return JSON 数组字符串；分组不存在或未配置时返回 空
     */
    @Select("SELECT sys_ai_group_models FROM sys_ai_group WHERE sys_ai_group_id = #{groupId}")
    String selectGroupModels(@Param("groupId") Integer groupId);
}
