package com.chua.starter.unified.server.support.service.impl;

import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.unified.server.support.entity.UnifiedExecuterItem;
import com.chua.starter.unified.server.support.mapper.UnifiedExecuterItemMapper;
import com.chua.starter.unified.server.support.service.UnifiedExecuterItemService;
@Service
public class UnifiedExecuterItemServiceImpl extends ServiceImpl<UnifiedExecuterItemMapper, UnifiedExecuterItem> implements UnifiedExecuterItemService{

}
