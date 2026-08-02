package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduQuestion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EduQuestionMapper extends BaseMapper<EduQuestion> {
}