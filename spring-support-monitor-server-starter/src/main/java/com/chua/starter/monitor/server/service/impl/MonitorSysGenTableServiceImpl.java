package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.net.NetUtils;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.server.entity.MonitorSysGenColumn;
import com.chua.starter.monitor.server.entity.MonitorSysGenTable;
import com.chua.starter.monitor.server.entity.MonitorSysGenTemplate;
import com.chua.starter.monitor.server.mapper.SysGenTableMapper;
import com.chua.starter.monitor.server.properties.GenProperties;
import com.chua.starter.monitor.server.query.Download;
import com.chua.starter.monitor.server.result.TemplateResult;
import com.chua.starter.monitor.server.service.MonitorSysGenColumnService;
import com.chua.starter.monitor.server.service.MonitorSysGenTableService;
import com.chua.starter.monitor.server.service.MonitorSysGenTemplateService;
import com.chua.starter.monitor.server.util.VelocityInitializer;
import com.chua.starter.monitor.server.util.VelocityUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.apache.commons.codec.CharEncoding.UTF_8;

/**
 *    
 * @author CH
 */     
@Service
public class MonitorSysGenTableServiceImpl extends ServiceImpl<SysGenTableMapper, MonitorSysGenTable> implements MonitorSysGenTableService {
    private final IdentifierGenerator identifierGenerator = new DefaultIdentifierGenerator(NetUtils.getLocalAddress());
    @Resource
    private MonitorSysGenColumnService sysGenColumnService;

    @Resource
    private MonitorSysGenTemplateService sysGenTemplateService;
    @Resource
    private GenProperties genProperties;

    @Override
    public byte[] downloadCode(Download download) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(outputStream);
        generatorCode(download.getTabIds(), zip, download);
        IoUtils.closeQuietly(zip);
        return outputStream.toByteArray();
    }

    @Override
    public List<TemplateResult> template(Integer tabId) {
        // 获取模板列表
        List<String> templates = Optional.of(VelocityUtils.getTemplateList(genProperties.getTemplatePath())).orElse(Collections.emptyList());
        List<TemplateResult> temp = new LinkedList<>();
        MonitorSysGenTable sysGenTable = baseMapper.selectById(tabId);
        List<MonitorSysGenColumn> sysGenColumns = sysGenColumnService.list(Wrappers.<MonitorSysGenColumn>lambdaQuery().eq(MonitorSysGenColumn::getTabId, tabId));

        List<MonitorSysGenTemplate> list = sysGenTemplateService.list(Wrappers.<MonitorSysGenTemplate>lambdaQuery().eq(MonitorSysGenTemplate::getGenId, sysGenTable.getGenId()).or().isNull(MonitorSysGenTemplate::getGenId));
        for (MonitorSysGenTemplate sysGenTemplate : list) {
            String fileName = sysGenTemplate.getTemplateName();
            if(StringUtils.isEmpty(fileName)) {
                continue;
            }
            temp.add(createTemplateResult(sysGenTable, sysGenColumns, sysGenTemplate));

        }
        Download download = new Download();
        for (String template : templates) {
            temp.add(createTemplateResult(template, sysGenTable, sysGenColumns, download));
        }
        String tabTplCategory = sysGenTable.getTabTplCategory();
        if (StringUtils.isNotEmpty(tabTplCategory)) {
            Set<String> strings = Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(tabTplCategory);
            for (String template : strings) {
                temp.add(createTemplateResult(template, sysGenTable, sysGenColumns, download));
            }
        }


        return temp;
    }



    /**
     * 创建模板结果
     *
     * @param template      样板
     * @param sysGenTable   sys-gen表
     * @param sysGenColumns sys gen柱
     * @param download      下载
     * @return {@link TemplateResult}
     */
    private TemplateResult createTemplateResult(String template, MonitorSysGenTable sysGenTable, List<MonitorSysGenColumn> sysGenColumns, Download download) {
        TemplateResult item = new TemplateResult();
        String fileName = FileUtils.getBaseName(template);
        VelocityInitializer.initFileVelocity();
        // 设置主键列信息
        setPkColumn(sysGenTable, sysGenColumns);
        VelocityContext context = VelocityUtils.prepareContext(sysGenTable, sysGenColumns, download);
        item.setName(fileName);
        item.setType(FileUtils.getExtension(fileName));
        try (StringWriter sw = new StringWriter()) {
            Template tpl = Velocity.getTemplate(template, UTF_8);
            tpl.merge(context, sw);
            item.setContent(sw.toString());

        } catch (IOException e) {
            log.error("", e);
        }
        return item;
    }
    private TemplateResult createTemplateResult(MonitorSysGenTable sysGenTable, List<MonitorSysGenColumn> sysGenColumns, MonitorSysGenTemplate sysGenTemplate) {
        TemplateResult item = new TemplateResult();
        VelocityInitializer.initVelocity();
        setPkColumn(sysGenTable, sysGenColumns);
        VelocityContext context = VelocityUtils.prepareContext(sysGenTable, sysGenColumns, new Download());

        // 设置主键列信息

        item.setName(sysGenTemplate.getTemplateName() + "." + sysGenTemplate.getTemplateType());
        item.setPath(VelocityUtils.getFileName(sysGenTable, new Download(), sysGenTemplate));
        item.setType(FileUtils.getExtension(sysGenTemplate.getTemplateName()));
        try (StringWriter sw = new StringWriter()) {
            Template tpl = Velocity.getTemplate(sysGenTemplate.getTemplateContent(), UTF_8);
            tpl.merge(context, sw);
            item.setContent(sw.toString());

        } catch (IOException e) {
            log.error("", e);
        }
        return item;
    }
    /**
     * 生成器代码
     *
     * @param tabId    tabId
     * @param zip      zip
     * @param download download
     */
    private void generatorCode(String tabId, ZipOutputStream zip, Download download) {
        // 查询表信息
        MonitorSysGenTable sysGenTable = baseMapper.selectById(tabId);
        List<MonitorSysGenColumn> sysGenColumns = sysGenColumnService.list(Wrappers.<MonitorSysGenColumn>lambdaQuery().eq(MonitorSysGenColumn::getTabId, tabId));
        VelocityInitializer.initVelocity();
        // 设置主键列信息
        setPkColumn(sysGenTable, sysGenColumns);

        VelocityContext context = VelocityUtils.prepareContext(sysGenTable, sysGenColumns, download);

        // 获取模板列表
        List<MonitorSysGenTemplate> list = sysGenTemplateService.list(Wrappers.<MonitorSysGenTemplate>lambdaQuery().eq(MonitorSysGenTemplate::getGenId, sysGenTable.getGenId()).or().isNull(MonitorSysGenTemplate::getGenId));
        for (MonitorSysGenTemplate sysGenTemplate : list) {
            String fileName = VelocityUtils.getFileName(sysGenTable, download, sysGenTemplate);
            if(StringUtils.isEmpty(fileName)) {
                continue;
            }
            // 渲染模板
            try {
                StringWriter sw = new StringWriter();
                Template tpl = Velocity.getTemplate(sysGenTemplate.getTemplateContent(), UTF_8);
                tpl.merge(context, sw);
                // 添加到zip
                zip.putNextEntry(new ZipEntry(fileName));
                IoUtils.write(zip, StandardCharsets.UTF_8, false, sw.toString());
                IoUtils.closeQuietly(sw);
                zip.flush();
                zip.closeEntry();
            } catch (IOException e) {
                log.error("渲染模板失败，表名：" + sysGenTable.getTabName(), e);
            }
        }

//        List<String> templates = VelocityUtils.getTemplateList(genProperties.getTemplatePath());
//        for (String template : templates) {
//            String fileName = VelocityUtils.getFileName(template, sysGenTable, download);
//            if(StringUtils.isEmpty(fileName)) {
//                continue;
//            }
//            // 渲染模板
//            try {
//                StringWriter sw = new StringWriter();
//                Template tpl = Velocity.getTemplate(template, UTF_8);
//                tpl.merge(context, sw);
//                // 添加到zip
//                zip.putNextEntry(new ZipEntry(fileName));
//                IoUtils.write(zip, StandardCharsets.UTF_8, false, sw.toString());
//                IoUtils.closeQuietly(sw);
//                zip.flush();
//                zip.closeEntry();
//            } catch (IOException e) {
//                log.error("渲染模板失败，表名：" + sysGenTable.getTabName(), e);
//            }
//        }
    }

    /**
     * 设置pk列
     * 设置主键列信息
     *
     * @param table         业务表信息
     * @param sysGenColumns sys gen柱
     */
    public void setPkColumn(MonitorSysGenTable table, List<MonitorSysGenColumn> sysGenColumns) {
        for (MonitorSysGenColumn sysGenColumn : sysGenColumns) {
            if("1".equals(sysGenColumn.getColIsPk())) {
                table.setTabPkColumn(sysGenColumn);
            }
        }

        if(null == table.getTabPkColumn()) {
            table.setTabPkColumn(CollectionUtils.findFirst(sysGenColumns));
        }
    }



}
