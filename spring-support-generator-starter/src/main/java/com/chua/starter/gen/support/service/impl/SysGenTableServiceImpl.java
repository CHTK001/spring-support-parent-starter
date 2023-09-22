package com.chua.starter.gen.support.service.impl;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.net.NetUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.starter.gen.support.entity.SysGenColumn;
import com.chua.starter.gen.support.entity.SysGenTable;
import com.chua.starter.gen.support.mapper.SysGenTableMapper;
import com.chua.starter.gen.support.service.SysGenColumnService;
import com.chua.starter.gen.support.service.SysGenTableService;
import com.chua.starter.gen.support.util.VelocityInitializer;
import com.chua.starter.gen.support.util.VelocityUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.apache.commons.codec.CharEncoding.UTF_8;

/**
 *    
 * @author CH
 */     
@Service
public class SysGenTableServiceImpl extends ServiceImpl<SysGenTableMapper, SysGenTable> implements SysGenTableService{
    private final IdentifierGenerator identifierGenerator = new DefaultIdentifierGenerator(NetUtils.getLocalAddress());
    @Resource
    private SysGenColumnService sysGenColumnService;
    @Override
    public byte[] downloadCode(String tabIds) {
       try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(outputStream);) {
           for (String tabId : tabIds.split(CommonConstant.SYMBOL_COMMA)) {
               generatorCode(tabId.trim(), zip);
           }
           return outputStream.toByteArray();
       } catch (IOException e) {
           throw new RuntimeException(e);
       }
    }

    /**
     * 生成器代码
     *
     * @param tabId tabId
     * @param zip  zip
     */
    private void generatorCode(String tabId, ZipOutputStream zip) {
        // 查询表信息
        SysGenTable sysGenTable = baseMapper.selectById(tabId);
        List<SysGenColumn> sysGenColumns = sysGenColumnService.list(Wrappers.<SysGenColumn>lambdaQuery().eq(SysGenColumn::getTabId, tabId));
        VelocityInitializer.initVelocity();
        // 设置主键列信息
        setPkColumn(sysGenTable, sysGenColumns);

        VelocityContext context = VelocityUtils.prepareContext(sysGenTable, sysGenColumns);

        // 获取模板列表
        List<String> templates = VelocityUtils.getTemplateList(sysGenTable.getTabTplCategory());
        for (String template : templates) {
            // 渲染模板
            try( StringWriter sw = new StringWriter();) {
                Template tpl = Velocity.getTemplate(template, UTF_8);
                tpl.merge(context, sw);
                // 添加到zip
                zip.putNextEntry(new ZipEntry(VelocityUtils.getFileName(template, sysGenTable)));
                IoUtils.write(zip, StandardCharsets.UTF_8, false, sw.toString());
                zip.flush();
                zip.closeEntry();
            } catch (IOException e) {
                log.error("渲染模板失败，表名：" + sysGenTable.getTabName(), e);
            }
        }
    }

    /**
     * 设置pk列
     * 设置主键列信息
     *
     * @param table         业务表信息
     * @param sysGenColumns sys gen柱
     */
    public void setPkColumn(SysGenTable table, List<SysGenColumn> sysGenColumns) {
        for (SysGenColumn sysGenColumn : sysGenColumns) {
            if("1".equals(sysGenColumn.getColIsPk())) {
                table.setTabPkColumn(sysGenColumn);
            }
        }

        if(null == table.getTabPkColumn()) {
            table.setTabPkColumn(sysGenColumns.get(0));
        }
    }


}
