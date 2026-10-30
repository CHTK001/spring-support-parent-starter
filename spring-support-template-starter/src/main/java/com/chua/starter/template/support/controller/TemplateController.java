package com.chua.starter.template.support.controller;

import com.chua.starter.mybatis.entity.PageResult;
import com.chua.starter.template.support.model.TemplateDesignerPayload;
import com.chua.starter.template.support.model.TemplatePageQuery;
import com.chua.starter.template.support.model.TemplateSaveRequest;
import com.chua.starter.template.support.entity.TemplateInfo;
import com.chua.starter.template.support.printer.PrinterConfig;
import com.chua.starter.template.support.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模板管理 控制器
 *
 * <p>模块前缀由 {@code httpConfig.moduleBaseUrl.template} 决定（默认 {@code /api/template}）；
 * 端点对齐设备模块，分页走 {@code POST/GET /page}。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@RestController
@RequestMapping("/api/template")
@RequiredArgsConstructor
public class TemplateController {

    /**
     * 模板服务
     */
    private final TemplateService templateService;

    /**
     * 分页查询模板
     *
     * @param query 分页与筛选条件
     * @return 模板分页结果
     */
    @GetMapping("/page")
    public PageResult<TemplateInfo> pageTemplates(TemplatePageQuery query) {
        return templateService.pageTemplates(query);
    }

    /**
     * 模板列表（不分页）
     *
     * @param query 筛选条件
     * @return 模板列表
     */
    @GetMapping("/list")
    public List<TemplateInfo> listTemplates(TemplatePageQuery query) {
        return templateService.listTemplates(query);
    }

    /**
     * 获取模板
     *
     * @param id 模板 标识
     * @return 模板信息实体
     */
    @GetMapping("/{id}")
    public TemplateInfo getTemplate(@PathVariable String id) {
        return templateService.getTemplate(id);
    }

    /**
     * 获取模板设计器数据
     *
     * @param id 模板 标识
     * @return 设计器数据（info + content + mode）
     */
    @GetMapping("/{id}/designer")
    public TemplateDesignerPayload designerTemplate(@PathVariable String id) {
        return templateService.designer(id);
    }

    /**
     * 新增模板
     *
     * @param request 模板保存请求
     * @return 模板信息实体
     */
    @PostMapping("/add")
    public TemplateInfo addTemplate(@RequestBody TemplateSaveRequest request) {
        return templateService.addTemplate(request);
    }

    /**
     * 更新模板
     *
     * @param id      模板 标识
     * @param request 模板保存请求
     * @return 更新后的模板信息实体
     */
    @PutMapping("/{id}")
    public TemplateInfo updateTemplate(@PathVariable String id, @RequestBody TemplateSaveRequest request) {
        return templateService.updateTemplate(id, request);
    }

    /**
     * 删除模板
     *
     * @param id 模板 标识
     */
    @DeleteMapping("/{id}")
    public void deleteTemplate(@PathVariable String id) {
        templateService.removeTemplate(id);
    }

    /**
     * 打印机列表（应用側 hiprint 连接地址与 Token）
     *
     * @return 打印机列表
     */
    @GetMapping("/printer")
    public List<PrinterConfig> printerList() {
        return templateService.printerList();
    }

    /**
     * 自发现打印机
     *
     * @return 打印机设备名列表
     */
    @GetMapping("/printer/discover")
    public List<String> discoverPrinters() {
        return templateService.discoverPrinters();
    }

    /**
     * 设置默认打印机
     *
     * @param name 打印机设备名
     * @return 是否设置成功
     */
    @PostMapping("/printer/default")
    public Boolean setDefaultPrinter(@RequestParam(required = false) String name) {
        return templateService.setDefaultPrinter(name);
    }

    /**
     * 删除打印机
     *
     * @param name 打印机设备名
     * @return 是否删除成功
     */
    @DeleteMapping("/printer/{name}")
    public Boolean removePrinter(@PathVariable String name) {
        return templateService.removePrinter(name);
    }

    /**
     * 设置打印机参数（包含地址/端口/Token）
     *
     * @param printer 打印机参数
     * @return 打印机参数
     */
    @PostMapping("/printer")
    public PrinterConfig setPrinter(@RequestBody PrinterConfig printer) {
        templateService.setPrinter(printer);
        return printer;
    }
}
