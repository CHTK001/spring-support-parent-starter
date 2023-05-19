package com.chua.starter.server.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.boren.biz.entity.dict.EleDict;
import com.chua.starter.server.mapper.DeptMapper;
import com.chua.starter.server.pojo.Dept;
import com.chua.starter.server.service.IDeptService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 机构 服务实现类
 * </p>
 *
 * @author yzj
 * @since 2021-12-08
 */
@Service
public class DeptServiceImpl extends ServiceImpl<DeptMapper, Dept> implements IDeptService {

    @Override
    public Dept getDeptByUser(Integer id) {
        return null;
    }

    @Override
    public List<Dept> getSanmen() {
        return baseMapper.selectList(Wrappers.lambdaQuery());
    }

    @Override
    public List<Dept> getSanmen1() {
        return baseMapper.selectList(Wrappers.lambdaQuery());
    }

    @Override
    public List<EleDict> getSanmen2() {
        return baseMapper.getSanmen2();
    }
}
