package com.chua.starter.gen.support.service.impl;

import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.gen.support.entity.SysGenConfig;
import com.chua.starter.gen.support.mapper.SysGenConfigMapper;
import com.chua.starter.gen.support.service.SysGenConfigService;
@Service
public class SysGenConfigServiceImpl extends ServiceImpl<SysGenConfigMapper, SysGenConfig> implements SysGenConfigService{

}
