package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 角色（人设）Mapper。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiRoleMapper extends BaseMapper<SysAiRole> {
}