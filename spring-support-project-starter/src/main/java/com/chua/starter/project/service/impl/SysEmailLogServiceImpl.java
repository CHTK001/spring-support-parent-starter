package com.chua.starter.project.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.project.mapper.SysEmailLogMapper;
import com.chua.starter.project.service.SysEmailLogService;
import com.chua.starter.entity.project.SysEmailLog;
import org.springframework.stereotype.Service;

@Service
public class SysEmailLogServiceImpl extends ServiceImpl<SysEmailLogMapper, SysEmailLog> implements SysEmailLogService {

}
