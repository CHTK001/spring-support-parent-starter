package com.chua.starter.unified.server.support.service.impl;

import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.unified.server.support.mapper.UnifiedProfileMapper;
import com.chua.starter.unified.server.support.entity.UnifiedProfile;
import com.chua.starter.unified.server.support.service.UnifiedProfileService;
/**
 *    
 * @author CH
 */     
@Service
public class UnifiedProfileServiceImpl extends ServiceImpl<UnifiedProfileMapper, UnifiedProfile> implements UnifiedProfileService{

}
