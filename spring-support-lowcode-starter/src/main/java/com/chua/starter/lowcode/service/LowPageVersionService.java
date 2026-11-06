package com.chua.starter.lowcode.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.lowcode.entity.LowFormPageVersion;
import com.chua.starter.lowcode.vo.LowFormPageVersionVO;

import java.util.List;

/**
 * 低代码-页面版本服务。
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface LowPageVersionService extends IService<LowFormPageVersion> {

    /**
     * 查询页面的所有版本。
     *
     * @param pageId 页面ID
     * @return 版本列表
     */
    List<LowFormPageVersionVO> listByPageId(Integer pageId);

    /**
     * 版本详情。
     *
     * @param versionId 版本ID
     * @return 版本
     */
    LowFormPageVersionVO detail(Integer versionId);
}
