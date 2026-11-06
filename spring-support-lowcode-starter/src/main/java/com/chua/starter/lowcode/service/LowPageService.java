package com.chua.starter.lowcode.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.lowcode.entity.LowFormPage;
import com.chua.starter.lowcode.entity.LowFormPageVersion;
import com.chua.starter.lowcode.vo.LowFormPageVO;
import com.chua.starter.lowcode.vo.LowFormPageVersionVO;

/**
 * 低代码-页面管理服务。
 *
 * <p>页面以 pageCode 为对外标识：设计器保存（继续设计）→ 发布（pagePublishStatus=1，version+1）→
 * 运行期通过 /code/{pageCode} 或 /api/{pageCode} 取数。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface LowPageService extends IService<LowFormPage> {

    /**
     * 页面分页列表，不带 Schema 正文。
     *
     * @param page      分页
     * @param condition 查询条件
     * @return 分页结果
     */
    Page<LowFormPageVO> page(Page<LowFormPage> page, LowFormPage condition);

    /**
     * 页面详情（包含 Schema 正文）。
     *
     * @param pageId 页面ID
     * @return 页面详情
     */
    LowFormPageVO detail(Integer pageId);

    /**
     * 通过页面编码查询详情，专为「编码查询」设计。
     *
     * @param pageCode 页面编码
     * @return 页面详情
     */
    LowFormPageVO detailByCode(String pageCode);

    /**
     * 新增页面。
     *
     * @param page 页面
     * @return 保存后的页面
     */
    LowFormPage savePage(LowFormPage page);

    /**
     * 修改页面。
     *
     * @param page 页面
     * @return 是否成功
     */
    boolean updatePage(LowFormPage page);

    /**
     * 删除页面。
     *
     * @param pageId 页面ID
     * @return 是否成功
     */
    boolean deletePage(Integer pageId);

    /**
     * 发布页面：生成版本快照、版本号 +1、设置发布状态并启用。
     *
     * @param pageId 页面ID
     * @return 是否成功
     */
    boolean publishPage(Integer pageId);

    /**
     * 回滚到指定版本：恢复 Schema、版本号、发布状态。
     *
     * @param versionId 版本ID
     * @return 是否成功
     */
    boolean rollbackToVersion(Integer versionId);

    /**
     * 页面的版本列表。
     *
     * @param pageId 页面ID
     * @return 版本列表
     */
    java.util.List<LowFormPageVersionVO> versions(Integer pageId);

    /**
     * 渲染器专用接口：获取已发布页面的 Schema，基于 code 查询。
     *
     * @param pageCode 页面编码
     * @return 已发布 Schema，未发布返回 null
     */
    String publishedSchema(String pageCode);
}
