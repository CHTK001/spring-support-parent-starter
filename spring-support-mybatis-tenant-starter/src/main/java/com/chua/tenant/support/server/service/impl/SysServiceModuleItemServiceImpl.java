package com.chua.tenant.support.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.tenant.support.common.entity.SysServiceModuleItem;
import com.chua.tenant.support.server.mapper.SysServiceModuleItemMapper;
import com.chua.tenant.support.server.service.SysServiceModuleItemService;
import org.springframework.stereotype.Service;

/**
 * 服务模块关联服务实现类
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/07
 */
@Service
public class SysServiceModuleItemServiceImpl extends ServiceImpl<SysServiceModuleItemMapper, SysServiceModuleItem>
        implements SysServiceModuleItemService {
}
