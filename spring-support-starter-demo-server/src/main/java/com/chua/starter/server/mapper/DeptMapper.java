package com.chua.starter.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.boren.biz.entity.dict.EleDict;
import com.chua.starter.server.pojo.Dept;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * <p>
 * 机构 Mapper 接口
 * </p>
 *
 * @author yzj
 * @since 2021-12-08
 */
@Mapper
public interface DeptMapper extends BaseMapper<Dept> {


    List<EleDict> getSanmen2();
}
