package com.chua.starter.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.server.mapper.DoorplateMapper;
import com.chua.starter.server.pojo.Doorplate;
import com.chua.starter.server.service.DoorplateService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author CH
 */
@Service
public class DoorplateServiceImpl extends ServiceImpl<DoorplateMapper, Doorplate> implements DoorplateService {

    @Resource
    private DoorplateMapper doorplateMapper;

    @Override
    public int insert(Doorplate record) {
        return doorplateMapper.insert(record);
    }

    @Override
    public int insertSelective(Doorplate record) {
        return doorplateMapper.insert(record);
    }

    @Override
    public List<Doorplate> test(String id) {
        return baseMapper.test(id);
    }

    @Override
    public List<Doorplate> test2(String id, String name) {
        return baseMapper.test2(id, name);
    }

}
