package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduKnowledgeFile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识卡片附件条目 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface EduKnowledgeFileMapper extends BaseMapper<EduKnowledgeFile> {
}
