package com.chua.starter.unified.server.support.service.impl;

import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.unified.server.support.mapper.UnifiedConfigMapper;
import com.chua.starter.unified.server.support.entity.UnifiedConfig;
import com.chua.starter.unified.server.support.service.UnifiedConfigService;
@Service
public class UnifiedConfigServiceImpl extends ServiceImpl<UnifiedConfigMapper, UnifiedConfig> implements UnifiedConfigService{

}
