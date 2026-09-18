package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiGroup;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
* AI 模型分组 映射器。
*
* @author CH
* @since 4.0.0
 */
@Mapper
public interface SysAiGroupMapper extends BaseMapper<SysAiGroup> {

    /**
    * 按条件查询分组列表（名称模糊搜索、状态过滤、排序）。
    *
    * @param params 查询参数（可包含 sysAI群体名称、sysAI群体状态）
    * @return 分组列表
    */
    List<Map<String, Object>> group(Map<String, Object> params);
}
