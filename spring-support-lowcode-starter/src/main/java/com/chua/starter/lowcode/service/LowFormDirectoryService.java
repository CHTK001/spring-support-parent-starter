package com.chua.starter.lowcode.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.lowcode.entity.LowFormDirectory;
import com.chua.starter.lowcode.vo.LowFormDirectoryVO;
import com.chua.starter.lowcode.vo.LowFormOptionVO;

import java.util.List;

/**
 * 低代码-目录服务。
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface LowFormDirectoryService extends IService<LowFormDirectory> {

    /**
     * 目录树，用于文件管理器风格的左侧导航。
     *
     * @return 目录树
     */
    List<LowFormDirectoryVO> tree();

    /**
     * 目录下拉选项。
     *
     * @return 选项列表
     */
    List<LowFormOptionVO> options();

    /**
     * 新增目录。
     *
     * @param directory 目录
     * @return 保存后的目录
     */
    LowFormDirectory addDirectory(LowFormDirectory directory);

    /**
     * 修改目录。
     *
     * @param directory 目录
     * @return 是否成功
     */
    boolean editDirectory(LowFormDirectory directory);

    /**
     * 删除目录，存在子目录或表单/页面时拒绝。
     *
     * @param directoryId 目录ID
     * @return 是否成功
     */
    boolean deleteDirectory(Integer directoryId);
}
