package com.chua.starter.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.boren.biz.entity.dict.EleDict;
import com.chua.starter.common.support.annotations.DS;
import com.chua.starter.server.pojo.Dept;

import java.util.List;

/**
 * <p>
 * 机构 服务类
 * </p>
 *
 * @author yzj
 * @since 2021-12-08
 */
public interface IDeptService extends IService<Dept> {
    /**
     * 查询结构
     *
     * @param id 用户ID
     * @return 机构
     */
    Dept getDeptByUser(Integer id);

    List<Dept> getSanmen();
    @DS("sanmen")
    List<Dept> getSanmen1();
    @DS("zxb,sanmen")
    List<EleDict> getSanmen2();
}
