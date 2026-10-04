package com.chua.starter.eduai.service.impl;

import com.chua.starter.common.support.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 教师端归属守卫单元测试。
 *
 * <p>教师端「管理我自己的班级学生」的核心安全断言是
 * {@link EduTeacherCenterServiceImpl#assertOwned(String, String)}：
 * 当前登录账号必须等于目标班级的 {@code class_owner_user_id}，否则拒绝。
 * 一旦这里放松，教师就能通过改 URL 里的 classId 去动别人的班与学生，
 * 而页面与编译都不会给出任何提示，因此必须由单测守住。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
class TeacherOwnershipGuardTest {

    /**
     * 归属一致时放行。
     */
    @Test
    @DisplayName("归属一致放行")
    void sameOwner_passes() {
        assertDoesNotThrow(() -> EduTeacherCenterServiceImpl.assertOwned("teacher-1", "teacher-1"));
    }

    /**
     * 归属不一致时拒绝（越权改他人班级）。
     */
    @Test
    @DisplayName("归属不一致拒绝")
    void differentOwner_rejected() {
        assertThrows(BusinessException.class,
                () -> EduTeacherCenterServiceImpl.assertOwned("teacher-1", "teacher-2"));
    }

    /**
     * 目标行归属为空（系统预置数据）时拒绝普通账号直接操作。
     */
    @Test
    @DisplayName("目标归属为空拒绝")
    void nullOwner_rejected() {
        assertThrows(BusinessException.class,
                () -> EduTeacherCenterServiceImpl.assertOwned("teacher-1", null));
    }

    /**
     * 当前账号为空（未登录）时拒绝，且不得因 equals 方向写反而误放行。
     */
    @Test
    @DisplayName("当前账号为空拒绝")
    void nullCurrent_rejected() {
        assertThrows(BusinessException.class,
                () -> EduTeacherCenterServiceImpl.assertOwned(null, "teacher-1"));
    }

    /**
     * 两端都为空时按「未登录 + 无归属」处理，仍拒绝。
     */
    @Test
    @DisplayName("两端都为空拒绝")
    void bothNull_rejected() {
        assertThrows(BusinessException.class,
                () -> EduTeacherCenterServiceImpl.assertOwned(null, null));
    }
}
