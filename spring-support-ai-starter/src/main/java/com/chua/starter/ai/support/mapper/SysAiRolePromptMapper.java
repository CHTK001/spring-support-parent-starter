package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiRolePrompt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * AI 角色分形态提示词映射器。
 *
 * @author CH
 */
@Mapper
public interface SysAiRolePromptMapper extends BaseMapper<SysAiRolePrompt> {

    /**
     * 查出某角色某形态的提示词行，<b>包含已逻辑删除的行</b>。
     *
     * <p>唯一键 {@code uk_role_prompt_mode (role_id, prompt_mode, user_id)} 不含删除标记列，
     * 删过一次的形态再保存会撞唯一键，只能先复活原行。</p>
     *
     * @param roleId     角色主键
     * @param promptMode 形态标识
     * @param userId     归属用户
     * @return 提示词行；不存在返回 null
     */
    @Select("SELECT sys_ai_role_prompt_id AS id, user_id AS userId, role_id AS roleId, "
            + "prompt_mode AS promptMode, sys_ai_role_prompt_content AS content, "
            + "sys_ai_role_prompt_status AS status, sys_ai_role_prompt_delete AS deleted "
            + "FROM sys_ai_role_prompt "
            + "WHERE role_id = #{roleId} AND prompt_mode = #{promptMode} AND user_id = #{userId} LIMIT 1")
    SysAiRolePrompt selectIncludingDeleted(@Param("roleId") Integer roleId,
                                           @Param("promptMode") String promptMode,
                                           @Param("userId") String userId);

    /**
     * 复活一条已逻辑删除的提示词行。
     *
     * @param id      行主键
     * @param content 正文
     * @param status  状态：1 启用，0 停用
     * @return 影响行数
     */
    @Update("UPDATE sys_ai_role_prompt SET sys_ai_role_prompt_content = #{content}, "
            + "sys_ai_role_prompt_status = #{status}, sys_ai_role_prompt_delete = 0 "
            + "WHERE sys_ai_role_prompt_id = #{id}")
    int revive(@Param("id") Integer id, @Param("content") String content, @Param("status") Integer status);
}