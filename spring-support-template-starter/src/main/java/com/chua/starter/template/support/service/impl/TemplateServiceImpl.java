package com.chua.starter.template.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.mybatis.entity.PageResult;
import com.chua.starter.template.support.entity.TemplateInfo;
import com.chua.starter.template.support.entity.TemplatePrinter;
import com.chua.starter.template.support.mapper.TemplateInfoMapper;
import com.chua.starter.template.support.mapper.TemplatePrinterMapper;
import com.chua.starter.template.support.model.TemplateDesignerPayload;
import com.chua.starter.template.support.model.TemplatePageQuery;
import com.chua.starter.template.support.model.TemplateSaveRequest;
import com.chua.starter.template.support.printer.PrinterConfig;
import com.chua.starter.template.support.service.TemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 模板服务实现
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@Service
public class TemplateServiceImpl implements TemplateService {

    private final TemplateInfoMapper templateInfoMapper;

    /**
     * 打印机配置映射器（配置落库，应用重启不丢默认打印机）
     */
    private final TemplatePrinterMapper templatePrinterMapper;

    public TemplateServiceImpl(TemplateInfoMapper templateInfoMapper,
                               TemplatePrinterMapper templatePrinterMapper) {
        this.templateInfoMapper = templateInfoMapper;
        this.templatePrinterMapper = templatePrinterMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TemplateInfo addTemplate(TemplateSaveRequest request) {
        TemplateInfo template = request.toEntity(null);
        template.setTemplateInfoCreator("-1");
        template.setTemplateInfoModifier("-1");
        template.setTemplateInfoCreatedAt(java.time.LocalDateTime.now());
        templateInfoMapper.insert(template);
        log.info("[Template] 新增模板: id={}, name={}", template.getTemplateInfoId(), template.getTemplateInfoName());
        return template;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TemplateInfo updateTemplate(String id, TemplateSaveRequest request) {
        TemplateInfo existing = templateInfoMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("模板不存在: " + id);
        }
        // 名称 / 副标题 / 语言 / 类型 / 状态 由请求覆盖，描述直接写，备注可选
        TemplateInfo template = request.toEntity(id);
        template.setTemplateInfoUpdatedAt(java.time.LocalDateTime.now());
        templateInfoMapper.updateById(template);
        log.info("[Template] 更新模板: id={}, name={}", id, template.getTemplateInfoName());
        return templateInfoMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeTemplate(String id) {
        TemplateInfo existing = templateInfoMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("模板不存在: " + id);
        }
        // 软删除，避免已发布模板的存量表被污染
        existing.setTemplateInfoDeleted(1);
        existing.setTemplateInfoUpdatedAt(java.time.LocalDateTime.now());
        templateInfoMapper.updateById(existing);
        log.info("[Template] 删除模板: id={}", id);
    }

    @Override
    public TemplateInfo getTemplate(String id) {
        return templateInfoMapper.selectById(id);
    }

    @Override
    public PageResult<TemplateInfo> pageTemplates(TemplatePageQuery query) {
        TemplatePageQuery q = query == null ? new TemplatePageQuery() : query.normalize();
        Page<TemplateInfo> page = new Page<>(q.getPage(), q.getSize());
        LambdaQueryWrapper<TemplateInfo> wrapper = queryWrapper(q);
        IPage<TemplateInfo> iPage = templateInfoMapper.selectPage(page, wrapper);
        return PageResult.copy(iPage);
    }

    @Override
    public List<TemplateInfo> listTemplates(TemplatePageQuery query) {
        LambdaQueryWrapper<TemplateInfo> wrapper = queryWrapper(query == null ? new TemplatePageQuery() : query);
        wrapper.last("LIMIT 500");
        return templateInfoMapper.selectList(wrapper);
    }

    @Override
    public TemplateDesignerPayload designer(String id) {
        TemplateInfo template = templateInfoMapper.selectById(id);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在: " + id);
        }
        TemplateDesignerPayload payload = new TemplateDesignerPayload();
        payload.setId(id);
        payload.setInfo(template);
        payload.setContent(template.getTemplateInfoContent());
        payload.setMode(TemplateDesignerPayload.Mode.design);
        return payload;
    }

    @Override
    public List<PrinterConfig> printerList() {
        // 默认打印机排在最前，其余按创建时间升序
        LambdaQueryWrapper<TemplatePrinter> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(TemplatePrinter::getTemplatePrinterDefault)
                .orderByAsc(TemplatePrinter::getTemplatePrinterCreatedAt);
        List<TemplatePrinter> printers = templatePrinterMapper.selectList(wrapper);
        List<PrinterConfig> result = new ArrayList<>(printers.size());
        for (TemplatePrinter printer : printers) {
            result.add(toConfig(printer));
        }
        return result;
    }

    @Override
    public List<String> discoverPrinters() {
        // 实际（局域网 / 本机）打印机发现由应用层或 hiprint 自带 Agent 完成，
        // 这里只返回已配置的打印机，避免未接入时应用侧一直提示“未找到打印机”。
        List<String> names = new ArrayList<>();
        for (PrinterConfig config : printerList()) {
            names.add(config.getName());
        }
        log.info("[Template] 发现打印机: {}", names);
        return names;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setDefaultPrinter(String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        TemplatePrinter printer = selectByName(name);
        if (printer == null) {
            log.warn("[Template] 设置默认打印机失败，打印机不存在: {}", name);
            return false;
        }
        clearDefaultFlag(printer.getTemplatePrinterId());
        log.info("[Template] 设置默认打印机: {}", name);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removePrinter(String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        TemplatePrinter printer = selectByName(name);
        if (printer == null) {
            log.warn("[Template] 打印机 {} 不存在", name);
            return false;
        }
        templatePrinterMapper.deleteById(printer.getTemplatePrinterId());
        log.info("[Template] 删除打印机: {}", name);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPrinter(PrinterConfig printer) {
        // 添加 / 编辑打印机连接参数（地址 / 端口 / Token）
        if (printer == null || !StringUtils.hasText(printer.getName())) {
            throw new IllegalArgumentException("打印机设备名不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        TemplatePrinter existing = selectByName(printer.getName());
        // 自动默认：当前该击首诗（数据库空白）的打印机，或请求显式指定为默认
        boolean defaultNow = Boolean.TRUE.equals(printer.getIsDefault())
                && existing == null
                && templatePrinterMapper.selectCount(null) == 0;
        if (existing == null) {
            TemplatePrinter entity = new TemplatePrinter();
            fill(entity, printer, now, defaultNow);
            templatePrinterMapper.insert(entity);
        } else {
            fill(existing, printer, now, defaultNow);
            templatePrinterMapper.updateById(existing);
        }
        if (Boolean.TRUE.equals(existing == null ? null : existing.getTemplatePrinterDefault())) {
            clearDefaultFlag(existing == null ? null : existing.getTemplatePrinterDefault());
        }
        log.info("[Template] 设置打印机: {}", printer.getName());
    }

    /**
     * 把参数填充到打印机实体上（新增时创建、编辑时覆盖）
     *
     * @param entity     目标实体
     * @param printer    参数
     * @param now        当前时间
     * @param defaultNow 是否设为默认
     */
    private static void fill(TemplatePrinter entity, PrinterConfig printer, LocalDateTime now, boolean defaultNow) {
        entity.setTemplatePrinterName(printer.getName());
        entity.setTemplatePrinterAddress(printer.getAddress());
        entity.setTemplatePrinterPort(printer.getPort());
        entity.setTemplatePrinterToken(printer.getToken());
        entity.setTemplatePrinterUsername(printer.getUsername());
        entity.setTemplatePrinterType(printer.getType());
        entity.setTemplatePrinterRemark(printer.getRemark());
        entity.setTemplatePrinterCreatedAt(now);
        entity.setTemplatePrinterUpdatedAt(now);
        entity.setTemplatePrinterDefault(defaultNow ? Boolean.TRUE : Boolean.FALSE);
    }

    /**
     * 按设备名查询打印机
     *
     * @param name 打印机设备名
     * @return 打印机实体，不存在返回 {@code null}
     */
    private TemplatePrinter selectByName(String name) {
        LambdaQueryWrapper<TemplatePrinter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TemplatePrinter::getTemplatePrinterName, name);
        wrapper.last("LIMIT 1");
        return templatePrinterMapper.selectOne(wrapper);
    }

    /**
     * 仅保留指定打印机为默认，其余全部下掉默认标记
     *
     * @param keepId 需要保留为默认的打印机主键
     */
    private void clearDefaultFlag(String keepId) {
        LambdaUpdateWrapper<TemplatePrinter> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(TemplatePrinter::getTemplatePrinterDefault, Boolean.FALSE)
                .eq(TemplatePrinter::getTemplatePrinterDefault, Boolean.TRUE)
                .ne(TemplatePrinter::getTemplatePrinterId, keepId);
        templatePrinterMapper.update(null, wrapper);
        LambdaUpdateWrapper<TemplatePrinter> setDefault = new LambdaUpdateWrapper<>();
        setDefault.set(TemplatePrinter::getTemplatePrinterDefault, Boolean.TRUE)
                .eq(TemplatePrinter::getTemplatePrinterId, keepId);
        templatePrinterMapper.update(null, setDefault);
    }

    /**
     * 实体转打印机参数（对外模型）
     *
     * @param entity 打印机实体
     * @return 打印机参数
     */
    private static PrinterConfig toConfig(TemplatePrinter entity) {
        PrinterConfig config = new PrinterConfig();
        config.setName(entity.getTemplatePrinterName());
        config.setAddress(entity.getTemplatePrinterAddress());
        config.setPort(entity.getTemplatePrinterPort());
        config.setToken(entity.getTemplatePrinterToken());
        config.setUsername(entity.getTemplatePrinterUsername());
        config.setType(entity.getTemplatePrinterType());
        config.setRemark(entity.getTemplatePrinterRemark());
        config.setIsDefault(Boolean.TRUE.equals(entity.getTemplatePrinterDefault()));
        return config;
    }

    /**
     * 构建模板查询条件（与 XML 的查询条件保持同一条 SQL 路径）
     *
     * @param q 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<TemplateInfo> queryWrapper(TemplatePageQuery q) {
        LambdaQueryWrapper<TemplateInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TemplateInfo::getTemplateInfoDeleted, 0);
        // 关键字走 name / subtitle / remark 模糊匹配
        if (StringUtils.hasText(q.getKeyword())) {
            wrapper.and(w -> w
                    .like(TemplateInfo::getTemplateInfoName, q.getKeyword())
                    .or().like(TemplateInfo::getTemplateInfoSubtitle, q.getKeyword())
                    .or().like(TemplateInfo::getTemplateInfoRemark, q.getKeyword()));
        }
        // 语言
        if (StringUtils.hasText(q.getLanguage())) {
            wrapper.eq(TemplateInfo::getTemplateInfoLanguage, q.getLanguage());
        }
        // 类型
        if (StringUtils.hasText(q.getType())) {
            wrapper.eq(TemplateInfo::getTemplateInfoType, q.getType());
        }
        // 状态
        if (StringUtils.hasText(q.getStatus())) {
            wrapper.eq(TemplateInfo::getTemplateInfoStatus, q.getStatus());
        }
        // 排序
        SFunction<TemplateInfo, ?> field = getSortByKey(q.getSortBy());
        if (field == null) {
            field = TemplateInfo::getTemplateInfoUpdatedAt;
        }
        if ("asc".equalsIgnoreCase(q.getSortOrder())) {
            wrapper.orderByAsc(field);
        } else {
            wrapper.orderByDesc(field);
        }
        return wrapper;
    }

    /**
     * 排序字段映射
     *
     * @param sortBy 排序字段
     * @return 排序字段
     */
    private static SFunction<TemplateInfo, ?> getSortByKey(String sortBy) {
        switch (sortBy == null ? null : sortBy.trim()) {
            case "name":
                return TemplateInfo::getTemplateInfoName;
            case "createdAt":
                return TemplateInfo::getTemplateInfoCreatedAt;
            default:
                return null;
        }
    }
}
