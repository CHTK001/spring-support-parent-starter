package com.chua.starter.oss.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.oss.support.pojo.SysOss;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OssSystemMapper extends BaseMapper<SysOss> {
}