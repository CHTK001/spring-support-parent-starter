package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.support.callback.DeviceCallbackResult;
import com.chua.starter.device.support.callback.DeviceLifecycleEvent;
import com.chua.starter.device.support.callback.DeviceLifecyclePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备生命周期回调管理接口（纯后端）。
 *
 * <p>回调实现本身是 Spring Bean，不通过本接口注册；本接口只提供「查看已注册回调」
 * 与「手工重放一条事件」两个运维入口，便于联调人脸录入等下游系统，以及失败后补发通知。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@RestController
@RequestMapping("/api/device/callbacks")
@RequiredArgsConstructor
public class DeviceCallbackController {

    /** 回调发布器 */
    private final DeviceLifecyclePublisher publisher;

    /**
     * 列出当前容器内已注册的回调实现名称。
     *
     * @return 回调名称列表；没有实现时为空数组
     */
    @GetMapping
    public ReturnResult<List<String>> list() {
        return ReturnResult.ok(publisher.callbackNames());
    }

    /**
     * 手工向全部回调重放一条生命周期事件（联调 / 失败补发）。
     *
     * @param event 事件负载；phase=FAILED 时下游按自身口径回滚
     * @return 每个回调的执行结果
     */
    @PostMapping("/dispatch")
    public ReturnResult<List<DeviceCallbackResult>> dispatch(@RequestBody DeviceLifecycleEvent event) {
        return ReturnResult.ok(publisher.publish(event));
    }
}
