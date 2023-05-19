package com.boren.biz.dict.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.boren.biz.entity.dict.EleDict;

/**
 * <p>
 * 字典表 服务类
 * </p>
 *
 * @author yzj
 * @since 2022-11-02
 */
public interface IEleDictService extends IService<EleDict> {
    public int count2();
}