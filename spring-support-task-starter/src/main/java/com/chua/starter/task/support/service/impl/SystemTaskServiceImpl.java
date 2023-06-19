package com.chua.starter.task.support.service.impl;

import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.task.support.mapper.SystemTaskMapper;
import com.chua.starter.task.support.pojo.SystemTask;
import com.chua.starter.task.support.service.SystemTaskService;
/**
 *    
 * @author CH
 */     
@Service
public class SystemTaskServiceImpl extends ServiceImpl<SystemTaskMapper, SystemTask> implements SystemTaskService{

    @Override
    public String createSystemTask(String type) {
        return null;
    }
}
