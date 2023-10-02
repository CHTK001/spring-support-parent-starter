package com.chua.starter.gen.support.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.gen.support.entity.SysGen;
import com.chua.starter.gen.support.entity.SysGenConfig;
import com.chua.starter.gen.support.mapper.SysGenMapper;
import com.chua.starter.gen.support.service.SysGenService;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import javafx.print.Collation;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Collection;

/**
 *    
 * @author CH
 */     
@Service
public class SysGenServiceImpl extends ServiceImpl<SysGenMapper, SysGen> implements SysGenService{

    @Override
    public SysGen getByIdWithType(Serializable genId) {
        return CollectionUtils.findFirst(baseMapper.selectList(new MPJLambdaWrapper<SysGen>()
                        .selectAll(SysGen.class)
                        .selectAs(SysGenConfig::getDbcType, "genType")
                        .innerJoin(SysGenConfig.class, SysGenConfig::getDbcId, SysGen::getDbcId)
                        .eq(SysGen::getGenId, genId)
                )
        );
    }
}
