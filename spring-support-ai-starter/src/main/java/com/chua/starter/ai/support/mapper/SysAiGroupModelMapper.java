package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiGroupModel;
import org.apache.ibatis.annotations.Mapper;

/**
* 分组-模型关联 映射器。
*
* @author CH
* @since 4.0.0
 */
@Mapper
public interface SysAiGroupModelMapper extends BaseMapper<SysAiGroupModel> {
}
