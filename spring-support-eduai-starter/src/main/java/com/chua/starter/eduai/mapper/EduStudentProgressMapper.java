package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduStudentProgress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学生模块学习进度 Mapper
 *
 * <p>本表所有列都由 {@link EduStudentProgress} 自己声明（不继承 SysBase），
 * 因此 BaseMapper 生成的列清单与库表一致，读写可直接走 BaseMapper，无需自定义 XML。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface EduStudentProgressMapper extends BaseMapper<EduStudentProgress> {
}
