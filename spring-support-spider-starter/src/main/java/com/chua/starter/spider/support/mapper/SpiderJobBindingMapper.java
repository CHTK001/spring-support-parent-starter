package com.chua.starter.spider.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.spider.support.domain.SpiderJobBinding;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务调度绑定 Mapper
 */
@Mapper
public interface SpiderJobBindingMapper extends BaseMapper<SpiderJobBinding> {
}
