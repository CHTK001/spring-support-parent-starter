package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiTavernMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 酒馆群消息 Mapper
 *
 * @author CH
 * @since 4.0.0.51
 */
@Mapper
public interface SysAiTavernMessageMapper extends BaseMapper<SysAiTavernMessage> {
}
