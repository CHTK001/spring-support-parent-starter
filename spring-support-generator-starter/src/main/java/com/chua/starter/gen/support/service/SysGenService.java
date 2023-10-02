package com.chua.starter.gen.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.gen.support.entity.SysGen;

import java.io.Serializable;

/**
 * @author CH
 */
public interface SysGenService extends IService<SysGen> {

    /**
     * 获取通过id具有类型
     *
     * @param genId gen id
     * @return {@link SysGen}
     */
    SysGen getByIdWithType(Serializable genId);
}
