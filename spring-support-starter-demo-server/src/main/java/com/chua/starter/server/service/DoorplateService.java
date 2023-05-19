package com.chua.starter.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.server.pojo.Doorplate;

import java.util.List;

/**
 * @author CH
 */
public interface DoorplateService extends IService<Doorplate> {


    int insert(Doorplate record);

    int insertSelective(Doorplate record);

    List<Doorplate> test(String id);

    List<Doorplate> test2(String id, String name);
}
