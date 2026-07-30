package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiGroupModel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分组-模型关联 Mapper。
 *
 * @author CH
 */
@Mapper
public interface SysAiGroupModelMapper extends BaseMapper<SysAiGroupModel> {
}
