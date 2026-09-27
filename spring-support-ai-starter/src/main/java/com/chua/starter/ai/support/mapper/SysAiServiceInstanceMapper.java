package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiServiceInstance;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 服务实例数据访问接口。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface SysAiServiceInstanceMapper extends BaseMapper<SysAiServiceInstance> {
}
