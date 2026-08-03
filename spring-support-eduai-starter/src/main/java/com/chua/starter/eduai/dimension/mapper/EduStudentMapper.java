package com.chua.starter.eduai.dimension.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.dimension.entity.EduStudent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EduStudentMapper extends BaseMapper<EduStudent> {
}