package com.chua.starter.unified.server.support.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.unified.server.support.entity.UnifiedLog;
import com.chua.starter.unified.server.support.mapper.UnifiedLogMapper;
import com.chua.starter.unified.server.support.service.UnifiedLogService;
import org.springframework.stereotype.Service;
@Service
public class UnifiedLogServiceImpl extends ServiceImpl<UnifiedLogMapper, UnifiedLog> implements UnifiedLogService{

}
