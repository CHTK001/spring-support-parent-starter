package com.chua.starter.unified.server.support.service.impl;

import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.unified.server.support.mapper.UnifiedMybatisMapper;
import com.chua.starter.unified.server.support.entity.UnifiedMybatis;
import com.chua.starter.unified.server.support.service.UnifiedMybatisService;
@Service
public class UnifiedMybatisServiceImpl extends ServiceImpl<UnifiedMybatisMapper, UnifiedMybatis> implements UnifiedMybatisService{

}
