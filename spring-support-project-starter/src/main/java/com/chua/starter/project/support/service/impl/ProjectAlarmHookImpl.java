package com.chua.starter.project.support.service.impl;

import com.chua.common.support.reflection.ReflectUtils;
import com.chua.starter.project.support.constants.ProjectConstants;
import com.chua.starter.project.support.entity.MsProject;
import com.chua.starter.project.support.service.ProjectAlarmHook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;

/**
 * 项目告警出口桥接（反射降级版）
 * <p>
 * 宿主装配 spring-support-monitor-starter 时，通过运行时反射查找
 * {@code AlarmEngineService} bean 并把项目守护指标喂给告警引擎；
 * 本模块与 monitor starter 之间零编译期依赖，未装配时静默跳过，
 * 不影响守护主流程。项目指标名沿用 monitor 告警体系约定，
 * 告警规则/静默/维护窗口/通知渠道由 monitor 侧统一管理。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectAlarmHookImpl implements ProjectAlarmHook {

    /** 告警引擎 bean 类型（全限定名，反射查找） */
    private static final String ALARM_ENGINE_CLASS =
            "com.chua.starter.monitor.support.service.alarm.AlarmEngineService";

    /** Spring 应用上下文（运行时反射查找告警引擎 bean） */
    private final ApplicationContext applicationContext;

    @Override
    public void evaluateProjectMetric(MsProject project, String metricName, double value) {
        if (project == null || project.getId() == null) {
            return;
        }
        Object engine = findAlarmEngine();
        if (engine == null) {
            return;
        }
        try {
            // [P3C 1.10 豁免] 跨模块解耦反射调用，保持原生反射以避免编译期强依赖
            Method evaluate = engine.getClass().getMethod("evaluate", Long.class, String.class, String.class, double.class);
            evaluate.invoke(engine, project.getId(), project.getMsProjectName(), metricName, value);
        } catch (Exception e) {
            log.debug("[project-alarm] 告警引擎调用失败（不影响主流程）: {}", e.getMessage());
        }
    }

    /**
     * 反射查找告警引擎 bean
     *
     * @return 引擎实例，未装配返回 空
     */
    private Object findAlarmEngine() {
        Class<?> engineClass = ReflectUtils.forName(ALARM_ENGINE_CLASS);
        if (engineClass == null) {
            return null;
        }
        try {
            String[] names = applicationContext.getBeanNamesForType(engineClass);
            if (names.length == 0) {
                return null;
            }
            return applicationContext.getBean(names[0]);
        } catch (Exception e) {
            log.debug("[project-alarm] 查找告警引擎 bean 失败: {}", e.getMessage());
            return null;
        }
    }
}
