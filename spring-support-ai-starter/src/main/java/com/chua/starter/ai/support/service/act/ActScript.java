package com.chua.starter.ai.support.service.act;

/**
 * 剧本模拟的幕次（Act）数据常量。
 *
 * <p>幕次文件（控制台落盘）：<pre>
 * {
 *   "actNo": 1,
 *   "title": "第一幕：群雄逐鹿",
 *   "description": "剧本概述",
 *   "worldTime": "第 1 天",
 *   "weather": "晴",
 *   "scenes": [
 *     { "sceneId": 1, "name": "洛阳城", "desc": "画面描述", "npcs": [1,2], "lines": [
 *         { "order": 1, "speaker": "曹操", "text": "..." } ],
 *       "playText": "",
 *       "mediaImage": { "taskId": null, "url": null },
 *       "mediaVideo": { "taskId": null, "url": null } } ],
 *   "transitions": [
 *     { "order": 1, "fromScene": 1, "toScene": 2, "text": "...",
 *       "mediaImage": { "taskId": null, "url": null },
 *       "mediaVideo": { "taskId": null, "url": null } } ],
 *   "props": [
 *     { "order": 1, "name": "青龙偃月刀", "link": "scene/1",
 *       "mediaImage": { "taskId": null, "url": null },
 *       "mediaVideo": { "taskId": null, "url": null } } ],
 *   "status": "writing",
 *   "ended": 0
 * }
 * </pre>
 *
 * <p>媒体句柄由 {@code GenerateService} 生成：{taskId} 存任务号，{url} 存生成结果地址。
 * 场景/过场/道具三处通用地带着 mediaImage / mediaVideo，生成完成后写入数据，
 * 从而素材与剧本一起落盘。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public final class ActScript {

    private ActScript() {
    }

    /** 场景键 */
    public static final String SCENES = "scenes";
    /** 过场键 */
    public static final String TRANSITIONS = "transitions";
    /** 道具键 */
    public static final String PROPS = "props";

    /** 场景 ID 键 */
    public static final String SCENE_SCENE_ID = "sceneId";
    /** 场景 名称 键 */
    public static final String SCENE_NAME = "name";
    /** 场景 描述 键 */
    public static final String SCENE_DESC = "desc";
    /** 场景 演员 键 */
    public static final String SCENE_NPCS = "npcs";
    /** 场景 台词 键 */
    public static final String SCENE_LINES = "lines";
    /** 场景 表演叙述 键 */
    public static final String SCENE_PLAY = "playText";
    /** 场景 演员名 键（演出正文里用于标注「谁在场」，剧本里可能缺） */
    public static final String SCENE_CAST = "cast";

    /** 过场 顺序 键 */
    public static final String TRANSITION_ORDER = "order";
    /** 过场 前场景 键 */
    public static final String TRANSITION_FROM = "fromScene";
    /** 过场 后场景 键 */
    public static final String TRANSITION_TO = "toScene";
    /** 过场 文字 键 */
    public static final String TRANSITION_TEXT = "text";

    /** 道具 顺序 键 */
    public static final String PROP_ORDER = "order";
    /** 道具 名字 键 */
    public static final String PROP_NAME = "name";
    /** 道具 关联 键 */
    public static final String PROP_LINK = "link";

    /** 媒体：图 键 */
    public static final String MEDIA_IMAGE = "mediaImage";
    /** 媒体：视频 键 */
    public static final String MEDIA_VIDEO = "mediaVideo";
    /** 媒体：任务ID 键 */
    public static final String MEDIA_TASK_ID = "taskId";
    /** 媒体：地址 键 */
    public static final String MEDIA_URL = "url";
    /** 媒体：状态 键 */
    public static final String MEDIA_STATUS = "status";
    /** 媒体：提示词 键 */
    public static final String MEDIA_PROMPT = "prompt";
    /** 媒体：本地推理（不发起生成） */
    public static final String MEDIA_OFFLINE = "offline";

    /** 媒体图片 Key */
    public static final String MEDIA_IMAGE_KEY = "mediaImage";
    /** 媒体视频 Key */
    public static final String MEDIA_VIDEO_KEY = "mediaVideo";
    /** 场景媒体图片 Key */
    public static final String SCENE_MEDIA_IMAGE = "mediaImage";
    /** 场景媒体视频 Key */
    public static final String SCENE_MEDIA_VIDEO = "mediaVideo";

    /** 幕次文件：状态 键 */
    public static final String ACT_STATUS = "status";
    /** 幕次文件：结束 键 */
    public static final String ACT_ENDED = "ended";
    /** 幕次文件：作者备注 */
    public static final String ACT_NOTES = "notes";

    /** 关联（"scene/1" / "transition/1" / "prop/1"）前缀 */
    public static final String LINK_SCENE = "scene";
    public static final String LINK_TRANSITION = "transition";
    public static final String LINK_PROP = "prop";
}
