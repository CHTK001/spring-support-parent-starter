package com.chua.starter.unified.server.support.service.impl;

import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.unified.server.support.entity.UnifiedExecuter;
import com.chua.starter.unified.server.support.mapper.UnifiedExecuterMapper;
import com.chua.starter.unified.server.support.service.UnifiedExecuterService;
@Service
public class UnifiedExecuterServiceImpl extends ServiceImpl<UnifiedExecuterMapper, UnifiedExecuter> implements UnifiedExecuterService{

}
