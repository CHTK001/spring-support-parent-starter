package com.chua.starter.unified.server.support.service.impl;

import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.unified.server.support.entity.UnifiedLog;
import com.chua.starter.unified.server.support.mapper.UnifiedLogMapper;
import com.chua.starter.unified.server.support.service.UnifiedLogService;
@Service
public class UnifiedLogServiceImpl extends ServiceImpl<UnifiedLogMapper, UnifiedLog> implements UnifiedLogService{

}
