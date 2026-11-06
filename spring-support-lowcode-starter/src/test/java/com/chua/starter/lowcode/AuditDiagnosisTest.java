package com.chua.starter.lowcode;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;

/**
 * 诊断：审计字段为什么没落库——查 MetaObjectHandler 是否注册进 GlobalConfig。
 *
 * @author CH
 * @since 4.0.0.51
 */
@SpringBootTest(classes = LowcodeTestApplication.class)
class AuditDiagnosisTest {

    @Autowired
    private ApplicationContext ctx;

    @Test
    void diagnose() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MetaObjectHandler beans:\n");
        Arrays.stream(ctx.getBeanNamesForType(MetaObjectHandler.class))
                .forEach(n -> sb.append("  ").append(n).append(" -> ")
                        .append(ctx.getBean(n).getClass().getName()).append('\n'));
        sb.append("=== MetaObjectHandler count: ")
                .append(ctx.getBeanNamesForType(MetaObjectHandler.class).length).append('\n');

        sb.append("=== GlobalConfig beans:\n");
        Arrays.stream(ctx.getBeanNamesForType(GlobalConfig.class))
                .forEach(n -> sb.append("  ").append(n).append('\n'));

        GlobalConfig gc = ctx.getBean(GlobalConfig.class);
        sb.append("GlobalConfig.metaObjectHandler = ")
                .append(gc.getMetaObjectHandler() == null ? "NULL" : gc.getMetaObjectHandler().getClass().getName())
                .append('\n');

        sb.append("=== lowcode beans:\n");
        Arrays.stream(ctx.getBeanDefinitionNames())
                .filter(n -> n.toLowerCase().contains("lowcode") || n.toLowerCase().contains("metaobject"))
                .forEach(n -> sb.append("  ").append(n).append('\n'));

        java.nio.file.Files.writeString(java.nio.file.Path.of("target/audit-diagnosis.txt"), sb.toString());
    }
}
