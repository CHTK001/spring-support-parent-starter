package com.chua.starter.device.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.device.provider.hikvision.open.HikvisionCloudEyeSession;
import com.chua.starter.device.support.entity.DevicePerson;
import com.chua.starter.device.support.model.FaceImageRequest;
import com.chua.starter.device.support.service.DevicePersonService;
import com.chua.starter.device.support.service.DeviceSessionManager;
import com.chua.starter.mybatis.entity.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 厂商人员台账控制器。
 *
 * @since 4.0.0.51
 * @author CH
 */
@RestController
@RequestMapping("/api/device/person")
@RequiredArgsConstructor
public class PersonController {

    /**
     * 人员服务
     */
    private final DevicePersonService personService;

    /**
     * 设备会话管理器（人脸操作取云眸会话令牌）
     */
    private final DeviceSessionManager sessionManager;

    /**
     * 新增人员。
     *
     * @param person 人员
     * @return 入库后的人员
     */
    @PostMapping
    public ReturnResult<DevicePerson> addPerson(@RequestBody DevicePerson person) {
        return ReturnResult.ok(personService.addPerson(person));
    }

    /**
     * 修改人员。
     *
     * @param id     主键
     * @param person 人员
     * @return 操作结果
     */
    @PutMapping("/{id}")
    public ReturnResult<Void> updatePerson(@PathVariable String id, @RequestBody DevicePerson person) {
        personService.updatePerson(id, person);
        return ReturnResult.ok();
    }

    /**
     * 删除人员。
     *
     * @param id 主键
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ReturnResult<Void> deletePerson(@PathVariable String id) {
        personService.deletePerson(id);
        return ReturnResult.ok();
    }

    /**
     * 查询单个人员。
     *
     * @param id 主键
     * @return 人员
     */
    @GetMapping("/{id}")
    public ReturnResult<DevicePerson> getPerson(@PathVariable String id) {
        return ReturnResult.ok(personService.getPerson(id));
    }

    /**
     * 查询人员列表。
     *
     * @param vendor  厂商标识，可选
     * @param keyword 关键字，可选
     * @return 人员列表
     */
    @GetMapping("/list")
    public ReturnResult<List<DevicePerson>> listPersons(@RequestParam(required = false) String vendor,
                                                        @RequestParam(required = false) String keyword) {
        return ReturnResult.ok(personService.listPersons(vendor, keyword));
    }

    /**
     * 分页查询人员。
     *
     * @param page    页码
     * @param size    每页条数
     * @param vendor  厂商标识，可选
     * @param keyword 关键字，可选
     * @return 分页结果
     */
    @GetMapping("/page")
    public PageResult<DevicePerson> pagePersons(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String vendor,
                                                @RequestParam(required = false) String keyword) {
        return personService.pagePersons(page, size, vendor, keyword);
    }

    /**
     * 录入/更新人员人脸（云眸官方 {@code basic/faces/update}）。
     *
     * @param id      人员主键
     * @param request 人脸图片
     * @return 操作结果
     */
    @PostMapping("/{id}/face")
    public ReturnResult<Void> updateFace(@PathVariable String id, @RequestBody FaceImageRequest request) {
        if (request == null || request.imageBase64() == null || request.imageBase64().isBlank()) {
            throw new IllegalArgumentException("人脸图片 imageBase64 不能为空");
        }
        DevicePerson person = requirePerson(id);
        cloudEye().updateFace(person.getDevicePersonExternalId(), request.imageBase64());
        return ReturnResult.ok();
    }

    /**
     * 删除人员人脸（云眸官方 {@code basic/faces/delete}）。
     *
     * @param id 人员主键
     * @return 操作结果
     */
    @DeleteMapping("/{id}/face")
    public ReturnResult<Void> deleteFace(@PathVariable String id) {
        DevicePerson person = requirePerson(id);
        cloudEye().deleteFace(person.getDevicePersonExternalId());
        return ReturnResult.ok();
    }

    /**
     * 读取人员，不存在时直接报错。
     *
     * @param id 主键
     * @return 人员
     */
    private DevicePerson requirePerson(String id) {
        DevicePerson person = personService.getPerson(id);
        if (person == null) {
            throw new IllegalArgumentException("人员不存在: " + id);
        }
        return person;
    }

    /**
     * 取当前在线的云眸会话；人脸接口只在该平台实现。
     *
     * @return 云眸会话
     */
    private HikvisionCloudEyeSession cloudEye() {
        return sessionManager.getAllSessions().values().stream()
                .filter(HikvisionCloudEyeSession.class::isInstance)
                .map(HikvisionCloudEyeSession.class::cast)
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "云眸无在线会话，无法操作人脸（人脸接口为云眸专属）"));
    }
}
