package com.chua.starter.unified.server.support.service.impl;

import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.unified.server.support.mapper.UnifiedComponentItemMapper;
import com.chua.starter.unified.server.support.entity.UnifiedComponentItem;
import com.chua.starter.unified.server.support.service.UnifiedComponentItemService;
@Service
public class UnifiedComponentItemServiceImpl extends ServiceImpl<UnifiedComponentItemMapper, UnifiedComponentItem> implements UnifiedComponentItemService{

}
