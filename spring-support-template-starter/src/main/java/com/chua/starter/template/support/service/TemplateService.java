package com.chua.starter.template.support.service;

import com.chua.starter.mybatis.entity.PageResult;
import com.chua.starter.template.support.model.TemplateDesignerPayload;
import com.chua.starter.template.support.model.TemplatePageQuery;
import com.chua.starter.template.support.model.TemplateSaveRequest;
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
     * 设置打印机参数（包含地址/端口/ Token）
     *
     * @param printer 打印机参数
     */
    void setPrinter(PrinterConfig printer);
}
