package com.chua.report.client.starter.report;

import com.chua.common.support.collection.ImmutableBuilder;
import com.chua.common.support.lang.page.Page;
import com.chua.oshi.support.Oshi;
import com.chua.oshi.support.Process;
import com.chua.report.client.starter.report.event.ProcessEvent;
import com.chua.report.client.starter.report.event.ReportEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Disk信息
 * @author CH
 * @since 2024/9/18
 */
@Slf4j
public class ProcessReport implements Report<List<ProcessEvent>>{
    @Override
    public ReportEvent<List<ProcessEvent>> report() {
        Page<Process> processPage = Oshi.newProcess(ImmutableBuilder.builderOfStringMap().put("pageSize", 10).newHashMap());
        ReportEvent<List<ProcessEvent>> objectReportEvent = new ReportEvent<>();
        objectReportEvent.setReportData(processPage.getData().stream().map(it -> {
            List<Integer> less = new LinkedList<>();
            ProcessEvent event = createProcessEvent(it, less, false);
            return event;
        }).filter(Objects::nonNull).toList());
//        objectReportEvent.setReportType(ReportEvent.ReportType.PROCESS);
        return objectReportEvent;
    }

    /**
     * 创建进程信息
     *
     * @param it   进程信息
     * @param less
     * @return 进程信息
     */
    private ProcessEvent createProcessEvent(Process it, List<Integer> less, boolean next) {
        if(less.contains(it.getProcessId())) {
            return null;
        }
        less.add(it.getProcessId());
        ProcessEvent event = new ProcessEvent();
        event.setName(it.getName());
        event.setValue(it.getValue()/ 1024/ 1024);
        event.setProcessId(it.getProcessId());
        event.setUser(it.getUser());
        event.setCommand(it.getCommand());
        event.setStartTime(it.getStartTime());
        event.setUpTime(it.getUpTime());
        event.setResidentSetSize(it.getResidentSetSize());
        event.setVirtualSize(it.getVirtualSize());
        event.setStatus(it.getStatus());
        event.setId(it.getId());
        List<ProcessEvent> children = new LinkedList<>();
        if(next) {
            List<Process> processes = Optional.ofNullable(Oshi.getProcess(it.getProcessId())).orElse(Collections.emptyList());
            for (Process process : processes) {
                ProcessEvent processEvent = createProcessEvent(process, less, true);
                if(null == processEvent) {
                    continue;
                }
                children.add(processEvent);
            }
        }
        event.setChildren(children);
        return event;
    }


}
