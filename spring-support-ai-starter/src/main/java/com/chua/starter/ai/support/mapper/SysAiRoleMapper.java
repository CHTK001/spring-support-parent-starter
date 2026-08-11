package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 角色（人设）Mapper。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface SysAiRoleMapper extends BaseMapper<SysAiRole> {
}