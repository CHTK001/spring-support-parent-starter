package com.chua.starter.unified.server.support.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.unified.server.support.entity.UnifiedComponentItem;
import com.chua.starter.unified.server.support.mapper.UnifiedComponentItemMapper;
import com.chua.starter.unified.server.support.service.UnifiedComponentItemService;
import org.springframework.stereotype.Service;
@Service
public class UnifiedComponentItemServiceImpl extends ServiceImpl<UnifiedComponentItemMapper, UnifiedComponentItem> implements UnifiedComponentItemService{

}
