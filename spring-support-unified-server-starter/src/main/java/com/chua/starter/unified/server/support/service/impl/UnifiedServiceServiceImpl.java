package com.chua.starter.unified.server.support.service.impl;

import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.unified.server.support.entity.UnifiedService;
import com.chua.starter.unified.server.support.mapper.UnifiedServiceMapper;
import com.chua.starter.unified.server.support.service.UnifiedServiceService;
@Service
public class UnifiedServiceServiceImpl extends ServiceImpl<UnifiedServiceMapper, UnifiedService> implements UnifiedServiceService{

}
