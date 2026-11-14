/**
 * 账号「用途能力」目录。
 *
 * <p>与后端的 {@code AccountCapability}（VERIFY / INBOX / SECRET_LIBRARY /
 * DEFAULT_SWITCH，决定按钮显隐的能力位）不是一回事：这里每一条是用户给一条账号
 * 打的用途标记，例如「发邮件」「大语言」。一条账号可以标记多个，存在
 * {@code sys_account.config.capabilities}（字符串数组），不新增表列、不改接口；
 * 后端 {@code SysAccount#config} 是 JSON 列，未知键原样回传。</p>
 */

/** 一条用途能力 */
export interface AccountCapabilityTag {
  /** 稳定标识：落库、判断都用它，改中文名不影响历史数据 */
  key: string;
  /** 中文名：图标提示与表单胶囊共用 */
  label: string;
  /** ReIcon 名称（element-plus 图标） */
  icon: string;
}

/** 可标记的用途能力（顺序即展示顺序） */
export const ACCOUNT_CAPABILITY_TAGS: AccountCapabilityTag[] = [
  { key: 'mail.send', label: '发邮件', icon: 'Promotion' },
  { key: 'mail.inbox', label: '收邮件', icon: 'Message' },
  { key: 'llm', label: '大语言', icon: 'ChatDotRound' },
  { key: 'agent', label: '智能体', icon: 'Cpu' },
  { key: 'sms', label: '短信', icon: 'Cellphone' },
  { key: 'api', label: '接口调用', icon: 'Link' },
  { key: 'verify', label: '凭据校验', icon: 'CircleCheck' },
  { key: 'storage', label: '对象存储', icon: 'Files' },
  { key: 'payment', label: '支付', icon: 'Coin' },
  { key: 'device', label: '设备接入', icon: 'Monitor' },
  { key: 'web', label: '网页登录', icon: 'Platform' },
  { key: 'oauth', label: '单点登录', icon: 'Key' },
  { key: 'webhook', label: '回调通知', icon: 'Bell' },
  { key: 'search', label: '搜索', icon: 'Search' },
  { key: 'map', label: '地图', icon: 'Location' },
  { key: 'translate', label: '翻译', icon: 'ChatLineSquare' },
  { key: 'image', label: '出图', icon: 'Picture' },
  { key: 'audio', label: '语音', icon: 'Microphone' },
  { key: 'video', label: '视频', icon: 'VideoCamera' },
  { key: 'data', label: '数据分析', icon: 'DataAnalysis' },
];

/** key → 定义 */
const TAG_BY_KEY = new Map<string, AccountCapabilityTag>(
  ACCOUNT_CAPABILITY_TAGS.map(tag => [tag.key, tag]),
);

/** 账号种类 → 未标记时的默认能力（有手填标记时以手填为准） */
const DEFAULT_KEYS_BY_KIND: Record<string, string[]> = {
  EMAIL: ['mail.inbox', 'mail.send'],
  SMS: ['sms'],
  API_KEY: ['api'],
};

/**
 * 读出一条账号标记的能力标识。
 *
 * <p>兼容三种历史写法：数组、逗号分隔字符串、单个字符串；不在目录里的键直接丢弃，
 * 这样删掉一条能力定义后旧数据不会在界面上留下空图标。</p>
 *
 * @param config 账号的 config JSON
 * @param kind   账号种类：config 里一条能力都没标记时按种类给默认值
 * @returns 去重后的能力标识（按目录顺序）
 */
export function readCapabilityKeys(
  config?: Record<string, unknown> | null,
  kind?: string | null,
): string[] {
  const raw = config?.capabilities;
  const list = Array.isArray(raw) ? raw : typeof raw === 'string' ? raw.split(',') : [];
  const picked = new Set(
    list
      .map(item => String(item ?? '').trim())
      .filter(key => TAG_BY_KEY.has(key)),
  );
  if (!picked.size && kind) {
    for (const key of DEFAULT_KEYS_BY_KIND[kind] || []) picked.add(key);
  }
  return ACCOUNT_CAPABILITY_TAGS.filter(tag => picked.has(tag.key)).map(tag => tag.key);
}

/**
 * 读出一条账号的能力定义。
 *
 * @param config 账号的 config JSON
 * @param kind   账号种类（未标记时用来兜底）
 * @returns 能力定义（按目录顺序）
 */
export function capabilityTagsOf(
  config?: Record<string, unknown> | null,
  kind?: string | null,
): AccountCapabilityTag[] {
  return readCapabilityKeys(config, kind).map(key => TAG_BY_KEY.get(key)!);
}

/**
 * 按标识取能力定义。
 *
 * @param key 能力标识
 * @returns 定义；不在目录里返回 undefined
 */
export function capabilityTag(key: string): AccountCapabilityTag | undefined {
  return TAG_BY_KEY.get(key);
}
