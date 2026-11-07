
// [P3C E1 豁免]

// [P3C E1 豁免]
package com.chua.starter.lowcode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 低代码独立联调服务：真实 HTTP 端口 + H2(MySQL 模式)，供前端 dev 联调用。
 *
 * <p>只启用自动装配，不扫描业务包：模块能力全部来自
 * {@code META-INF/spring/...AutoConfiguration.imports}，验证「加依赖即生效」。</p>
 *
 * <p>用法：{@code java -cp ... LowcodeDevServer}，默认端口 18082。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class LowcodeDevServer {

    /**
     * 启动联调服务。
     *
     * @param args 命令行参数
     * @throws Exception 建表失败
     */
    public static void main(String[] args) throws Exception {
        System.setProperty("server.port", System.getProperty("server.port", "18082"));
        System.setProperty("spring.datasource.url",
                System.getProperty("spring.datasource.url",
                        "jdbc:h2:mem:lowcode_dev;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE"));
        System.setProperty("spring.datasource.driver-class-name", "org.h2.Driver");
        System.setProperty("spring.datasource.username", "sa");
        System.setProperty("spring.datasource.password", "");
        System.setProperty("spring.sql.init.mode", "never");
        System.setProperty("plugin.mybatis.data.scope.enable", "false");
        System.setProperty("plugin.oauth.enable", "false");
        System.setProperty("plugin.osgi.enabled", "false");
        System.setProperty("plugin.lowcode.enable", "true");

        ConfigurableApplicationContext context = SpringApplication.run(LowcodeDevServer.class, args);

        // 显式回放本模块建表与种子脚本
        DataSource dataSource = context.getBean(DataSource.class);
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/init/V1.0__init_lowcode.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/init/V1.0__initdata_lowcode.sql"));
        }
        System.out.println("LOWCODE-DEV-SERVER-READY port=" + System.getProperty("server.port"));
    }
}
