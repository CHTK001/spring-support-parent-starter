package com.chua.starter.script.watchdog;

import com.chua.starter.script.ScriptExtension;
import com.chua.starter.script.bean.ScriptBean;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 监听器
 *
 * @author CH
 */
@ScriptExtension("file")
public class FileWatchdog implements Watchdog {

    private final Map<File, ScriptBean> cache = new ConcurrentHashMap<>();
    private final Map<String, FileAlterationMonitor> monitorMap = new ConcurrentHashMap<>();
    private int timeout;

    @Override
    public void register(Object source, ScriptBean scriptBean) {
        if (source instanceof File) {
            initRefresh((File) source, scriptBean);
        }
    }

    @Override
    public void timeout(int timeout) {
        this.timeout = timeout;
    }

    private void initRefresh(File source, ScriptBean scriptBean) {
        cache.put(source, scriptBean);
        registerObserver(source);
        scriptBean.refresh(analysis(source));
    }

    private String analysis(File source) {
        try (FileInputStream fileInputStream = new FileInputStream(source);) {
            String s = IOUtils.toString(fileInputStream, UTF_8);
            String[] split = s.split("\r\n", 2);
            String s1 = split[0].trim();
            if (s1.startsWith("package") || s1.startsWith("import")) {
                return s;
            }
            return split[1];
        } catch (IOException ignored) {
        }
        return null;
    }

    private void registerObserver(File source) {
        File parentFile = source.getParentFile();
        monitorMap.computeIfAbsent(parentFile.getAbsolutePath(), new Function<String, FileAlterationMonitor>() {
            @Override
            public FileAlterationMonitor apply(String s) {
                long interval = TimeUnit.SECONDS.toMillis(timeout);
                FileAlterationObserver fileAlterationObserver = new FileAlterationObserver(parentFile);
                fileAlterationObserver.addListener(new FileAlterationListenerAdaptor() {
                    @Override
                    public void onFileChange(File file) {
                        refresh(file);
                        super.onFileChange(file);
                    }

                    @Override
                    public void onFileDelete(File file) {
                        super.onFileDelete(file);
                    }
                });

                FileAlterationMonitor monitor = new FileAlterationMonitor(interval, fileAlterationObserver);
                try {
                    monitor.start();
                } catch (Exception ignored) {
                }
                return monitor;
            }
        });


    }

    private void refresh(File file) {
        ScriptBean scriptBean = cache.get(file);
        if (null == scriptBean) {
            return;
        }
        scriptBean.refresh(analysis(file));
    }


    @Override
    public void close() throws Exception {
        monitorMap.values().forEach(monitor -> {
            try {
                monitor.stop();
            } catch (Exception ignored) {
            }
        });
    }
}
