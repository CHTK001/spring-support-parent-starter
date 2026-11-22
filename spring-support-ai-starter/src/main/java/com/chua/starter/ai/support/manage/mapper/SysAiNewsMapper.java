package com.chua.starter.ai.support.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.manage.store.SysAiNews;
import org.apache.ibatis.annotations.Mapper;

/**
 * AIHOT 精选资讯 映射器。
 *
 * @since 4.0.0.70
 * @author CH
 */
@Mapper
public interface SysAiNewsMapper extends BaseMapper<SysAiNews> {
}
