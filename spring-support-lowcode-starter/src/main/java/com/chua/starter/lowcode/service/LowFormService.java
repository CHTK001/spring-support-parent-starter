package com.chua.starter.lowcode.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.lowcode.entity.LowForm;
import com.chua.starter.lowcode.entity.LowFormVersion;
import com.chua.starter.lowcode.vo.LowFormVO;
import com.chua.starter.lowcode.vo.LowFormVersionVO;

/**
 * 低代码-表单管理服务。
 *
 * <p>表单以 formCode 为对外标识：设计器保存（继续设计）→ 发布（formPublishStatus=1，version+1）→
 * 运行期通过 /code/{formCode} 或 /api/{formCode} 取数。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface LowFormService extends IService<LowForm> {

    /**
     * 表单分页列表，不带 Schema 正文。
     *
     * @param page      分页
     * @param condition 查询条件
     * @return 分页结果
     */
    Page<LowFormVO> page(Page<LowForm> page, LowForm condition);

    /**
     * 表单详情（包含 Schema 正文，设计器/渲染器使用）。
     *
     * @param formId 表单ID
     * @return 表单详情
     */
    LowFormVO detail(Integer formId);

    /**
     * 通过表单编码查询详情，专为「编码查询」设计。
     *
     * @param formCode 表单编码
     * @return 表单详情
     */
    LowFormVO detailByCode(String formCode);

    /**
     * 新增表单。
     *
     * @param form 表单
     * @return 保存后的表单
     */
    LowForm saveForm(LowForm form);

    /**
     * 修改表单。
     *
     * @param form 表单
     * @return 是否成功
     */
    boolean updateForm(LowForm form);

    /**
     * 删除表单。
     *
     * @param formId 表单ID
     * @return 是否成功
     */
    boolean deleteForm(Integer formId);

    /**
     * 发布表单：生成版本快照、版本号 +1、设置发布状态并启用。
     *
     * @param formId 表单ID
     * @return 是否成功
     */
    boolean publishForm(Integer formId);

    /**
     * 回滚到指定版本：恢复 Schema、版本号、发布状态。
     *
     * @param versionId 版本ID
     * @return 是否成功
     */
    boolean rollbackToVersion(Integer versionId);

    /**
     * 表单的版本列表。
     *
     * @param formId 表单ID
     * @return 版本列表
     */
    java.util.List<LowFormVersionVO> versions(Integer formId);

    /**
     * 渲染器专用接口：获取已发布表单的 Schema，基于 code 查询。
     *
     * @param formCode 表单编码
     * @return 已发布 Schema，未发布返回 null
     */
    String publishedSchema(String formCode);
}
