package com.chua.starter.unified.server.support.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.unified.server.support.entity.UnifiedComponent;
import com.chua.starter.unified.server.support.mapper.UnifiedComponentMapper;
import com.chua.starter.unified.server.support.service.UnifiedComponentService;
import org.springframework.stereotype.Service;
@Service
public class UnifiedComponentServiceImpl extends ServiceImpl<UnifiedComponentMapper, UnifiedComponent> implements UnifiedComponentService{

}
