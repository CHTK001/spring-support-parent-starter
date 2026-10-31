package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.entity.SysAiReidLibrary;
import com.chua.starter.ai.support.mapper.SysAiReidLibraryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 人脸重识别底库列表的字段契约测试。
 *
 * <p><b>需求：</b>观测一经写入底库（{@code POST /v2/reid/observe}），
 * {@code GET /v2/reid/library} 回出的每一条记录，必须包含前端
 * {@code ReidPage.vue}（类型 {@code ReidLibraryItem}、计算列 {@code cards}）消费的全部字段：</p>
 *
 * <pre>
 * id, trackId, cameraId, name, group, faceName, lat, lng, thumbnail, createTime
 * </pre>
 *
 * <p>只锁字段契约，不拉起 Spring 上下文——底层用动态代理桩代替 MyBatis 查询
 * （和 {@code OcrDeliveryContractTest} 的手法一致）。直接调用 {@link ReidLibraryLinkService#listMine}
 * 把真实产出打出来比对。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@DisplayName("人脸重识别底库列表字段契约")
class ReidLibraryFieldShapeTest {

    /** 前端 {@code ReidPage.vue} 消费的底库字段（project/ai 的 {@code ReidLibraryItem}）。 */
    private static final List<String> FRONTEND_FIELD_NAMES = List.of(
            "id", "trackId", "cameraId", "name", "group", "faceName", "lat", "lng", "thumbnail", "createTime");

    private static final String ACCOUNT = "test-user";
    private static final String TRACK_ID = "track-a1b2c3d4";
    private static final String VECTOR_ID = "reid-1234567890abcdef";
    private static final String CAMERA_ID = "cam-1";
    private static final String SOURCE_ID = "frame-20261031T100000Z";
    private static final LocalDateTime CREATE_TIME = LocalDateTime.of(2026, 10, 31, 10, 0, 0);
    private static final BigDecimal LAT = new BigDecimal("31.2304");
    private static final BigDecimal LNG = new BigDecimal("121.4737");

    /**
     * 模拟 {@code AiReidServiceImpl#persistObservation} 落出的 3 条观测行：
     * 新观测、已关联人脸底库的观测、旧灰度行（group 列 NULL，建表前写的数据）。
     */
    private static List<SysAiReidLibrary> sampleRows() {
        List<SysAiReidLibrary> rows = new ArrayList<>(3);

        // 新观测：group 随 meta 落库（"default"），faceId / faceName 未关联
        SysAiReidLibrary row1 = new SysAiReidLibrary();
        row1.setUserId(ACCOUNT);
        row1.setVectorId(VECTOR_ID);
        row1.setTrackId(TRACK_ID);
        row1.setCameraId(CAMERA_ID);
        row1.setName(CAMERA_ID + "@" + SOURCE_ID);
        row1.setMeta("{\"group\":\"default\",\"sourceId\":\"" + SOURCE_ID + "\"}");
        row1.setGroupName("default");
        row1.setLat(LAT);
        row1.setLng(LNG);
        row1.setThumbnail("data:image/png;base64,iVBOR");
        row1.setCreateTime(CREATE_TIME);
        row1.setUpdateTime(CREATE_TIME);
        row1.setStatus(1);
        rows.add(row1);

        // 已关联人脸底库的观测：有 faceId/faceName，分组为组 A
        SysAiReidLibrary row2 = new SysAiReidLibrary();
        row2.setUserId(ACCOUNT);
        row2.setVectorId(VECTOR_ID + "-2");
        row2.setTrackId(TRACK_ID + "-2");
        row2.setCameraId("cam-2");
        row2.setName("cam-2@frame-2");
        row2.setMeta("{\"group\":\"groupA\",\"faceId\":42,\"faceName\":\"李四\"}");
        row2.setGroupName("groupA");
        row2.setFaceId(42);
        row2.setFaceName("李四");
        row2.setLat(LAT);
        row2.setLng(LNG);
        row2.setThumbnail("data:image/png;base64,iVBOR");
        row2.setCreateTime(CREATE_TIME.minusDays(1));
        row2.setUpdateTime(CREATE_TIME);
        row2.setStatus(1);
        rows.add(row2);

        // 旧灰度行：group 列 NULL（建表前写的数据），其余完整
        SysAiReidLibrary row3 = new SysAiReidLibrary();
        row3.setUserId(ACCOUNT);
        row3.setVectorId(VECTOR_ID + "-3");
        row3.setTrackId(TRACK_ID);
        row3.setCameraId(CAMERA_ID);
        row3.setName("cam-1@frame-3");
        row3.setMeta("{\"group\":null}");
        row3.setGroupName(null);
        row3.setLat(null);
        row3.setLng(null);
        row3.setThumbnail(null);
        row3.setCreateTime(CREATE_TIME.minusDays(3));
        row3.setUpdateTime(CREATE_TIME);
        row3.setStatus(1);
        rows.add(row3);

        return rows;
    }

    /**
     * 把底库映射器打桩：查询返回样品行集（observe 落库后的底库数据），
     * 写方法按 MyBatis 语义返回零值。
     */
    private static SysAiReidLibraryMapper stubMapper(List<SysAiReidLibrary> rows) {
        return (SysAiReidLibraryMapper) Proxy.newProxyInstance(
                SysAiReidLibraryMapper.class.getClassLoader(),
                new Class<?>[]{SysAiReidLibraryMapper.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        String name = method.getName();
                        if ("selectList".equals(name)) {
                            // 基线：只捞当前账号、未停用的观测（status=1），按 createTime 倒序。
                            // 参数是 LambdaQueryWrapper，桩里用样品数据代替 SQL。
                            return rows;
                        }
                        if ("selectOne".equals(name)) {
                            return rows.isEmpty() ? null : rows.get(0);
                        }
                        if ("selectCount".equals(name) || "selectPage".equals(name)) {
                            return rows.size();
                        }
                        if ("insert".equals(name) || "updateById".equals(name) || "update".equals(name)) {
                            return 1;
                        }
                        if ("delete".equals(name) || "deleteById".equals(name)) {
                            return 1;
                        }
                        return null;
                    }
                });
    }

    /**
     * 主契约：后端回流字段集包含前端消费的全部字段。
     */
    @Test
    @DisplayName("listMine 回流字段集包含前端 ReidLibraryItem 消费的全部字段")
    void fieldSetCoversFrontendFields() {
        List<Map<String, Object>> items = new ReidLibraryLinkService(stubMapper(sampleRows()))
                .listMine(ACCOUNT, List.of("default"));

        assertEquals(3, items.size(), "3 行观测回列出 3 条");
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            for (String field : FRONTEND_FIELD_NAMES) {
                assertTrue(item.containsKey(field),
                        "第 " + i + " 行必须包含前端消费的字段 " + field + "；实际字段=" + item.keySet());
                // 字段必须存在：前端类型有 null 兜底，空值只是展示位，字段名不能省。
                assertNotNull(item.get(field),
                        "第 " + i + " 行 " + field + " 必须有键（字段名不能省）");
            }
        }
    }

    /**
     * 字段的<strong>类型</strong>要和 {@code ReidLibraryItem} 对齐——
     * {@code lat}/{@code lng} 是 JSON 数字（BigDecimal），{@code createTime} 是 ISO 时间字符串。
     */
    @Test
    @DisplayName("lat/lng 为 BigDecimal（JSON 数字），createTime 为 ISO 字符串")
    void fieldTypesMatchFrontendModels() {
        List<Map<String, Object>> items = new ReidLibraryLinkService(stubMapper(sampleRows()))
                .listMine(ACCOUNT, List.of("default"));

        Map<String, Object> recent = items.get(0);
        // lat/lng 走 BigDecimal，Jackson 序列化为 JSON 数字，前端类型 string|number|null 直接接受
        assertTrue(recent.get("lat") instanceof BigDecimal,
                "lat 必须是 BigDecimal 才能转发为 JSON 数字；实际=" + recent.get("lat").getClass());
        assertTrue(recent.get("lng") instanceof BigDecimal,
                "lng 必须是 BigDecimal 才能转发为 JSON 数字；实际=" + recent.get("lng").getClass());
        // createTime 是 LocalDateTime.toString()，形如 2026-10-31T10:00:00，
        // 前端 toEpoch 用 new Date(String.replace('T', ' ')) 解析。
        String createTime = (String) recent.get("createTime");
        assertNotNull(createTime, "createTime 必须可解析");
        assertTrue(createTime.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}$"),
                "createTime 必须是 ISO 本地时间字符串，后端回送 " + createTime);
    }

    /**
     * 旧灰度行：group 列 NULL 时，{@code listMine} 必须归一成 {@code default}，
     * 否则前端 {@code o.group ?? group.value} 的兜底会暴露已归档的分组。
     */
    @Test
    @DisplayName("NULL 分组行：归一成 default（不触发前端遗憾兜底）")
    void nullGroupRowsNormalizeToDefault() {
        List<Map<String, Object>> items = new ReidLibraryLinkService(stubMapper(sampleRows()))
                .listMine(ACCOUNT, List.of("default"));

        Map<String, Object> legacy = items.get(2);
        assertEquals("default", legacy.get("group"),
                "NULL 分组（建表前灰度数据）应归一成 default，不能省字段");
    }

    /**
     * 扫描每条记录额外多回的字段：只要以 `metadata`（演练面信息）等更多字段存在，
     * 且不影响前端（它的类型不包含这些键），不视为缺陷。
     */
    @Test
    @DisplayName("额外回传字段安全：metadata/faceId 围绕用户的冗余信息")
    void extraFieldsAreSafeRedundancies() {
        List<Map<String, Object>> items = new ReidLibraryLinkService(stubMapper(sampleRows()))
                .listMine(ACCOUNT, List.of("default"));

        Map<String, Object> recent = items.get(0);
        // 前端 ReidLibraryItem 没有 faceId / metadata 字段，但不影响：后端回透过它们传导档案信息。
        assertTrue(recent.containsKey("faceId"), "应继续回传 faceId（关联人脸底库行）");
        assertTrue(recent.containsKey("metadata"), "应继续回传 metadata（面信息）");
        assertEquals(TRACK_ID, recent.get("trackId"), "trackId 是基准观测的权威位置");
    }
}
