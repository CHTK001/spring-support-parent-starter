/**
 * 密钥厂商（「用途 / 厂商」）词表。
 *
 * <p>一处维护三件事：下拉里的名称与 logo、详情里的中文翻译、无 logo 时的字母徽章。
 * 品牌图统一用仓库共享的 {@code /images/providers/*}（与 AI 控制台的供应商图标
 * 同一份目录），拿不到时退化成品牌色字母徽章，不阻塞页面。</p>
 *
 * <p>{@code value} 是落库值（小写英文），{@code label} 只用于展示；
 * 历史数据里的自填厂商（如 {@code smtp} / {@code pop3}）统一按原样展示。</p>
 */

/** 单个厂商定义 */
export interface SecretProvider {
  /** 落库值（账号 / 密钥表里的 provider） */
  value: string
  /** 中文名 */
  label: string
  /** 品牌主色（字母徽章底色 / 单色 logo 染色） */
  color: string
  /** 共享品牌图文件名（{@code /images/providers/} 下）；空表示无 logo */
  logo?: string
  /**
   * 是否单色图形（需要按品牌色染色）。
   *
   * <p>纯黑/纯白的单色标直接当图片渲染，会在深色或白色背景上「消失」；
   * 带自带底色或渐变的多色标（腾讯云蓝底、Azure 渐变、火山引擎双色）保持原色。</p>
   */
  tint?: boolean
}

/** 支持的厂商（下拉顺序：国内云 → 国际云 → 对象存储 → 支付 → 自定义） */
export const SECRET_PROVIDERS: SecretProvider[] = [
  { value: 'aliyun', label: '阿里云', color: '#FF6A00', logo: 'aliyun-bailian.svg', tint: true },
  { value: 'tencent', label: '腾讯云', color: '#007AFF', logo: 'tencent.svg' },
  { value: 'huawei', label: '华为云', color: '#C7000B', logo: 'huawei.svg', tint: true },
  { value: 'baidu', label: '百度智能云', color: '#2932E1', logo: 'baidu.svg', tint: true },
  { value: 'volcengine', label: '火山引擎', color: '#006AFF', logo: 'volcengine.svg' },
  { value: 'jd', label: '京东云', color: '#E1251B', logo: 'jdcloud.png' },
  { value: 'qiniu', label: '七牛云', color: '#00A3E0', logo: 'qiniu-ai.png' },
  { value: 'ucloud', label: '优刻得', color: '#2C6DE0', logo: 'ucloud.png' },
  { value: 'aws', label: 'Amazon Web Services', color: '#FF9900', logo: 'aws-bedrock.png' },
  { value: 'azure', label: 'Microsoft Azure', color: '#0078D4', logo: 'azure.svg' },
  { value: 'gcp', label: 'Google Cloud', color: '#4285F4', logo: 'google.svg' },
  { value: 'cloudflare', label: 'Cloudflare', color: '#F38020', logo: 'cloudflare.svg', tint: true },
  { value: 'minio', label: 'MinIO', color: '#C72E49', logo: 'minio.svg', tint: true },
  { value: 'alipay', label: '支付宝', color: '#1677FF', logo: 'alipay.svg', tint: true },
  { value: 'wechat', label: '微信支付', color: '#07C160', logo: 'wechat.svg', tint: true },
  { value: 'custom', label: '自定义', color: '#909399', logo: 'custom.svg', tint: true },
]

/** 按落库值取厂商定义（未知值原样兜底） */
export function providerOf(value?: string | null): SecretProvider {
  const key = String(value || '').trim().toLowerCase()
  const hit = SECRET_PROVIDERS.find((item) => item.value === key)
  if (hit) return hit
  const raw = String(value || '').trim()
  return { value: raw || 'custom', label: raw || '自定义', color: '#909399' }
}

/** 厂商中文名（未知值原样返回，不硬翻成「其他」） */
export function providerLabel(value?: string | null): string {
  return providerOf(value).label
}

/** 品牌图地址；空串表示没有 logo，调用方走字母徽章 */
export function providerLogo(value?: string | null): string {
  const file = providerOf(value).logo
  return file ? `/images/providers/${file}` : ''
}
