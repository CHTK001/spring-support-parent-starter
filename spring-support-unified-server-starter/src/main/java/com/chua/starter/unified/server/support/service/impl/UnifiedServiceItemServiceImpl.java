package com.chua.starter.unified.server.support.service.impl;

import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.unified.server.support.entity.UnifiedServiceItem;
import com.chua.starter.unified.server.support.mapper.UnifiedServiceItemMapper;
import com.chua.starter.unified.server.support.service.UnifiedServiceItemService;
@Service
public class UnifiedServiceItemServiceImpl extends ServiceImpl<UnifiedServiceItemMapper, UnifiedServiceItem> implements UnifiedServiceItemService{

}
