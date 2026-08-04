package com.chua.starter.spider;

import com.chua.common.support.lang.code.ReturnResult;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.Executors;

/**
 * 真正独立的测试启动器（不依赖 PowerShell 进程管理）。
 *
 * <p>启动 Spring Boot 后，主线程 sleep 30 秒，期间服务可被调用验证。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@SpringBootApplication(scanBasePackages = {
        "com.chua.starter.spider.controller",
        "com.chua.starter.spider.service",
        "com.chua.starter.spider.service.impl",
        "com.chua.starter.spider.flow",
        "com.chua.starter.spider.flow.handler",
        "com.chua.starter.spider.executor"
})
public class SpiderRunApp {
    /**
     * 入口。
     */
    public static void main(String[] args) throws Exception {
        // 启动一个守护线程，每 1s 打印 "still alive" 让外部知道进程没死
        Thread t = new Thread(() -> {
            int n = 0;
            while (n < 600) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                n++;
                if (n % 30 == 0) {
                    System.out.println("[KEEP-ALIVE] still running, t=" + n + "s");
                }
            }
        }, "keep-alive");
        t.setDaemon(true);
        t.start();

        SpringApplication.run(SpiderRunApp.class, args);
        System.out.println("[SHUTDOWN] Spring Boot exited");
        // 不要 exit，主线程继续跑让 web 服务存活
        // 不行，SpringApplication.run() 阻塞到上下文关闭，如果控制台 dump 后还阻塞，就保留
    }
}
