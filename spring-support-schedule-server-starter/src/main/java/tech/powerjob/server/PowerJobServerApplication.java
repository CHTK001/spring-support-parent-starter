package tech.powerjob.server;

import tech.powerjob.server.common.utils.PropertyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * powerjob-server entry
 *
 * @author tjq
 * @since 2020/3/29
 */
@Slf4j
@EnableScheduling
@SpringBootApplication
public class PowerJobServerApplication {

    public static void main(String[] args) {
        pre();
        SpringApplication.run(PowerJobServerApplication.class, args);
    }

    private static void pre() {
        PropertyUtils.init();
    }

}
