package com.chua.starter.template.support.service;

import com.chua.starter.mybatis.entity.PageResult;
import com.chua.starter.template.support.model.TemplateDesignerPayload;
import com.chua.starter.template.support.model.TemplatePageQuery;
import com.chua.starter.template.support.model.TemplateSaveRequest;
import com.chua.starter.template.support.model.TemplateSourceRequest;
import com.chua.starter.template.support.model.TemplateSourceDetail;
import com.chua.starter.template.support.entity.TemplateInfo;
import com.chua.starter.template.support.printer.PrinterConfig;

import java.util.List;

/**
 * 模板服务接口
 *
 * @since 4.0.0.52
 * @author CH
 */
public interface TemplateService {

    /**
     * 新增模板
     *
     * @param request 模板保存请求
     * @return 模板信息实体
     */
    TemplateInfo addTemplate(TemplateSaveRequest request);

    /**
     * 更新模板
     *
     * @param id      模板 标识
     * @param request 模板保存请求
     * @return 更新后的模板信息实体
     */
    TemplateInfo updateTemplate(String id, TemplateSaveRequest request);

    /**
     * 删除模板（软删除）
     *
     * @param id 模板 标识
     */
    void removeTemplate(String id);

    /**
     * 获取模板
     *
     * @param id 模板 标识
     * @return 模板信息实体
     */
    TemplateInfo getTemplate(String id);

    /**
     * 分页查询
     *
     * @param query 分页与筛选条件
     * @return 模板分页结果
     */
    PageResult<TemplateInfo> pageTemplates(TemplatePageQuery query);

    /**
     * 列表查询
     *
     * @param query 筛选条件
     * @return 模板列表
     */
    List<TemplateInfo> listTemplates(TemplatePageQuery query);

    /**
     * 设计器数据（info + content + mode）
     *
     * @param id 模板 标识
     * @return 设计器完整数据
     */
    TemplateDesignerPayload designer(String id);

    /**
     * 模板详情（按模板 ID 取信息）
     *
     * <p>自定义模板（hiprint）直接由库内行给出；固定模板（message / paper）会向
     * 来源模块补问一次，拿到三方模板 ID、归属账号、推送渠道与占位符参数，
     * 字段形态对齐 { MessageRequest}（发送一条模板消息所需的东西）。</p>
     *
     * @param id 模板 标识（template_info_id）
     * @return 模板详情
     */
    TemplateSourceDetail detail(String id);

    /**
     * 把来源模块（消息 / 组卷）的模板行接入统一模板库，幂等注册
     *
     * <p>已存在同一来源的模板（source + source_id 完全相同）时更新；
     * 未注册时新建。仅更新 template_info_source/source_id/provider 指向自身的行，
     * 不触碰 hiprint 自建的绘制模板。</p>
     *
     * @param request 来源模板注册请求
     * @return 注册后的模板信息实体
     */
    TemplateInfo registerTemplate(TemplateSourceRequest request);

    /**
     * 打印机列表（应用侧 hiprint 连接地址与 Token）
     *
     * @return 打印机列表
     */
    List<PrinterConfig> printerList();

    /**
     * 自发现打印机
     *
     * @return 打印机设备名
     */
    List<String> discoverPrinters();

    /**
     * 设置默认打印机
     *
     * @param name 打印机设备名
     * @return 是否设置成功
     */
    boolean setDefaultPrinter(String name);

    /**
     * 删除打印机（按设备名）
     *
     * @param name 打印机设备名
     * @return 是否删除成功
     */
    boolean removePrinter(String name);

    /**
     * 设置打印机参数（包含地址/端口/ Token）
     *
     * @param printer 打印机参数
     */
    void setPrinter(PrinterConfig printer);
}
