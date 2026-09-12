package com.chua.starter.console.store;

import com.chua.starter.console.scan.AnnotationSwitchScanner;
import com.chua.starter.console.scan.SwitchInventory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
   * 启动重置器（applicationrunner：上下文就绪、处理器 mapping 完整后执行）：
   * 扫描注解计算指纹，与 sqlite 存储的上次指纹比对——不一致（注解更新）则清空覆盖库并落新指纹；
 * 一致则保留运行时覆盖（页面临时调整在注解未变时跨重启存活）。
 *
 * @author CH
 * @since 2026/09/04
 */
@Slf4j
public class ConsoleStartupResetter implements ApplicationRunner {

    private final AnnotationSwitchScanner scanner; // scanner
    private final ConsoleSqliteStore store; // 存储

    /**
     * 控制台startupresetter。
     * @param scanner scanner
     * @param store 存储
     */
    public ConsoleStartupResetter(AnnotationSwitchScanner scanner, ConsoleSqliteStore store) {
        this.scanner = scanner;
        this.store = store;
    }

    @Override
    public void run(ApplicationArguments args) {
        SwitchInventory inventory = scanner.scan();
        String current = store.getFingerprint();
        if (current == null || !current.equals(inventory.getFingerprint())) {
            int resetCount = store.resetOverrides();
            store.setFingerprint(inventory.getFingerprint());
            log.info("[Console] 注解指纹比对: {} -> {}，覆盖库已{}（清空 {} 条）",
                    current == null ? "(首次)" : current, inventory.getFingerprint(),
                    current == null ? "初始化" : "重置", resetCount);
        } else {
            log.info("[Console] 注解指纹比对: 无变化，覆盖库保留（{} 条功能覆盖、{} 条灰度覆盖）",
                    store.listFeatureOverrides().size(), store.listGrayOverrides().size());
        }
    }
}
