package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiTavernMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 酒馆群成员 Mapper
 *
 * @author CH
 * @since 4.0.0.51
 */
@Mapper
public interface SysAiTavernMemberMapper extends BaseMapper<SysAiTavernMember> {
}
