package com.chua.starter.script.watcher;

import com.chua.common.support.watcher.AbstractWatcher;
import com.chua.common.support.watcher.EventObserver;
import com.chua.common.support.watcher.WatcherEvent;
import com.chua.common.support.watcher.WatcherOption;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import java.io.File;

/**
 * @author CH
 */
public class IoWatcher extends AbstractWatcher {

    private final long interval = 500;
    private FileAlterationMonitor monitor;

    public IoWatcher(String name, WatcherOption watcherOption) {
        super(name, watcherOption);
    }

    @Override
    public void run() {
        FileAlterationObserver fileAlterationObserver = new FileAlterationObserver(name);
        fileAlterationObserver.addListener(new FileAlterationListenerAdaptor() {
            @Override
            public void onFileChange(File file) {
                onModify(WatcherEvent.MODIFY, EventObserver.builder()
                        .currentPath(file.getParent())
                        .triggerPath(file.getName())
                        .source(file)
                        .event(WatcherEvent.MODIFY)
                        .build());
            }

            @Override
            public void onFileDelete(File file) {
                onDelete(WatcherEvent.DELETE, EventObserver.builder()
                        .currentPath(file.getParent())
                        .triggerPath(file.getName())
                        .source(file)
                        .event(WatcherEvent.DELETE)
                        .build());
            }
            @Override
            public void onFileCreate(File file) {
                onCreate(WatcherEvent.CREATE, EventObserver.builder()
                        .currentPath(file.getParent())
                        .triggerPath(file.getName())
                        .source(file)
                        .event(WatcherEvent.CREATE)
                        .build());
            }
        });

        this.monitor = new FileAlterationMonitor(interval, fileAlterationObserver);
        try {
            monitor.start();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void addPath(String path) {
        monitor.addObserver(new FileAlterationObserver(path));
    }

    @Override
    public void stopWatch() {
        try {
            monitor.stop();
        } catch (Exception ignored) {
        }
    }
}
