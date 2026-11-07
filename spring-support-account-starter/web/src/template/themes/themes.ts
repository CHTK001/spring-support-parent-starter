/**
 * 模板主题（PPT 风格） —— 主题就是一套色彩 + 字体的可替换协议
 *
 * 模板的内容是 hiprint 的 JSON 描述（每个元素的 style 决定了绘制表现），
 * 所以"主题"不负责画图，只负责把设计描述里的色彩/字体映射到一套
 * 预设的值上，让模板整体呈现一种"主题皮肤"。
 *
 * 切换主题的代价：该主题被写入设计描述（保存后生效到预览/打印侧）
 * 与设计器历史的几点：
 *   - 主题替换的是一个"值"的映射，不是智能识别，所以显示/编辑都一致
 *   - 应用主题后，画布会被重建，撤销/重做[data] 保持原样（不记录主题的切换）
 *
 * 使用方式：
 *   import { THEMES, applyTheme } from '../themes/themes'
 *   // 1. 从设计的 JSON 中得出主题
 *   const json = tpl.getJson()
 *   const themed = applyTheme(json, THEMES[1])
 *   // 2. 回填画布 / 保存
 */

/** 主题色盘（用于映射 hiprint 元素的 style 字段） */
export interface ThemePalette {
  /** 主色（强调色） */
  primary: string
  /** 辅助色 */
  secondary: string
  /** 点缀色 */
  accent: string
  /** 正文文字色 */
  text: string
  /** 次要文字色 */
  textSecondary: string
  /** 标题文字色 */
  textTitle: string
  /** 页面背景色 */
  background: string
  /** 面板/卡片底色 */
  surface: string
  /** 分割线/边框色 */
  border: string
  /** 页眉文字色 */
  header: string
  /** 页脚文字色 */
  footer: string
}

/** 主题字体 */
export interface ThemeFonts {
  /** 正文字体 */
  fontFamily: string
  /** 标题字体 */
  titleFontFamily?: string
  /** 基础字号（pt） */
  fontSize?: string | number
  /** 标题字号 */
  heading?: { h1?: string | number; h2?: string | number; h3?: string | number }
  /** 行高 */
  lineHeight?: string
}

/**
 * 一个主题
 */
export interface Theme {
  /** 主题标识（可持久化的 id） */
  id: string
  /** 主题名称 */
  name: string
  /** 主题描述 */
  description?: string
  /** 色盘 */
  palette: ThemePalette
  /** 字体 */
  fonts: ThemeFonts
  /** 深色系 */
  isDark?: boolean
}

/** PPT 风格的默认主题集 */
export const THEMES: Theme[] = [
  {
    id: 'default',
    name: '默认',
    description: '不应用任何主题，保留纯设计描述',
    palette: {
      primary: '#0052d9',
      secondary: '#00355a',
      accent: '#ff8006',
      text: '#222222',
      textSecondary: '#5d5d5d',
      textTitle: '#111111',
      background: '#ffffff',
      surface: '#ffffff',
      border: '#d9d9d9',
      header: '#333333',
      footer: '#777777'
    },
    fonts: {
      fontFamily: 'Microsoft YaHei',
      fontSize: '12'
    }
  },
  {
    id: 'light',
    name: '浅色',
    description: '清爽的浅色主题，适用于文档/报表类模板',
    palette: {
      primary: '#1677ff',
      secondary: '#0050b3',
      accent: '#fa8c16',
      text: '#222222',
      textSecondary: '#595959',
      textTitle: '#001529',
      background: '#f5f7fa',
      surface: '#ffffff',
      border: '#e8e8e8',
      header: '#172b4d',
      footer: '#8c8c8c'
    },
    fonts: {
      fontFamily: 'Microsoft YaHei',
      titleFontFamily: 'Microsoft YaHei',
      fontSize: '12'
    }
  },
  {
    id: 'blue',
    name: '商务蓝',
    description: 'PPT 商务风格的蓝色主题',
    palette: {
      primary: '#0357aa',
      secondary: '#00367a',
      accent: '#d97706',
      text: '#333333',
      textSecondary: '#666666',
      textTitle: '#0357aa',
      background: '#f0f5ff',
      surface: '#ffffff',
      border: '#adc6ff',
      header: '#0357aa',
      footer: '#8592c0'
    },
    fonts: {
      fontFamily: 'Microsoft YaHei',
      titleFontFamily: 'Microsoft YaHei',
      fontSize: '12'
    }
  },
  {
    id: 'green',
    name: '里绿',
    description: '柔和的绿色主题',
    palette: {
      primary: '#089983',
      secondary: '#07544a',
      accent: '#f87171',
      text: '#333333',
      textSecondary: '#666666',
      textTitle: '#089983',
      background: '#ecfdf5',
      surface: '#ffffff',
      border: '#a7f3d0',
      header: '#07544a',
      footer: '#6ee7b7'
    },
    fonts: {
      fontFamily: 'Microsoft YaHei',
      titleFontFamily: 'Microsoft YaHei',
      fontSize: '12'
    }
  },
  {
    id: 'dark',
    name: '夜览',
    description: '深色主题，适用于多媒体/展厅模板',
    palette: {
      primary: '#6366f1',
      secondary: '#4f46e5',
      accent: '#f59e0b',
      text: '#e4e4e7',
      textSecondary: '#a1a1aa',
      textTitle: '#ffffff',
      background: '#0f172a',
      surface: '#1e293b',
      border: '#334155',
      header: '#e2e8f0',
      footer: '#94a3b8'
    },
    fonts: {
      fontFamily: 'Microsoft YaHei',
      titleFontFamily: 'Microsoft YaHei',
      fontSize: '12'
    }
  },
  {
    id: 'orange',
    name: '活力橙',
    description: 'PPT 活力风格的橙色主题',
    palette: {
      primary: '#ea580c',
      secondary: '#b45309',
      accent: '#1677ff',
      text: '#333333',
      textSecondary: '#666666',
      textTitle: '#9a3412',
      background: '#fff7ed',
      surface: '#ffffff',
      border: '#fed7aa',
      header: '#9a3412',
      footer: '#fb923c'
    },
    fonts: {
      fontFamily: 'Microsoft YaHei',
      titleFontFamily: 'Microsoft YaHei',
      fontSize: '12'
    }
  },
  {
    id: 'purple',
    name: '商务紫',
    description: '优雅的主题色',
    palette: {
      primary: '#7c3aed',
      secondary: '#5b21b6',
      accent: '#f97316',
      text: '#1e1b4b',
      textSecondary: '#4f46e5',
      textTitle: '#7c3aed',
      background: '#f5f3ff',
      surface: '#ffffff',
      border: '#ddd6fe',
      header: '#5b21b6',
      footer: '#a78bfa'
    },
    fonts: {
      fontFamily: 'Microsoft YaHei',
      titleFontFamily: 'Microsoft YaHei',
      fontSize: '12'
    }
  }
]

/**
 * 主题色盘的值（仅用于判断一个色值"是否有值"）
 */
const PALETTE_KEYS: (keyof ThemePalette)[] = [
  'primary', 'secondary', 'accent', 'text', 'textSecondary', 'textTitle',
  'background', 'surface', 'border', 'header', 'footer'
]

/**
 * 把主题映射到 hiprint 的设计描述上
 *
 * @param content 设计描述（JSON 字符串 / 对象）
 * @param theme 主题
 * @returns 应用主题后的设计描述（JSON 字符串）
 */
export function applyTheme(content: string | ThemeContent, theme: Theme): string {
  const json = parseContent(content)
  if (!json) return JSON.stringify(content ?? '{}')

  // ---- 页面级：字体 / 背景色 ----
  const page: Record<string, any> = json.printPage ?? {}
  if (theme.fonts.fontFamily) page.fontFamily = theme.fonts.fontFamily
  if (theme.fonts.fontSize !== undefined) page.fontSize = theme.fonts.fontSize
  if (theme.palette.background) page.background = theme.palette.background
  json.printPage = page

  // ---- 水平线 / 垂直线 ----
  for (const key of ['printHorizontalRule', 'printVerticalRule'] as const) {
    const rule: Record<string, any> | undefined = json[key]
    if (!rule) continue
    const style = rule.style ?? rule
    if (style.color) style.color = theme.palette.border
  }

  // ---- 所有模块：元素 ----
  const modules: any[] = json.printModules ?? json.modules ?? []
  for (const module of modules) {
    const elements = module.printElements ?? module.elements ?? []
    for (const element of elements) {
      // 样式的 widget 级
      const style = element.style ?? {}
      element.style = style

      // 文字色（正文）
      if (style.color) {
        style.color = theme.palette.text
      }
      // 标题色（以 printText 长度/结构判断："标题" 元素未加粗的不算，统一交给 text 层）
      // 这里不做启发式判断：主题换的是色彩，字级由用户负责

      // 背景/面
      if (style.background) {
        style.background = theme.palette.surface
      }

      // 边框色
      const border = style.border
      if (border) {
        if (border.color) border.color = theme.palette.border
        if (border.borderColor) border.borderColor = theme.palette.border
        if (border.strokeColor) border.strokeColor = theme.palette.border
      }

      // 字体
      if (style.fontFamily) style.fontFamily = theme.fonts.fontFamily
      if (style.fontStyle) style.fontStyle = 'normal'
      if (style.fontWeight) style.fontWeight = 'normal'

      // 字号
      if (style.fontSize) style.fontSize = theme.fonts.fontSize

      // 标题字号（由 heading 驱动，如果没有则走 fontSize）
      const heading = theme.fonts.heading
      if (heading && element.printText && typeof element.printText === 'string' && element.printText?.length <= 4) {
        style.fontSize = heading.h1 ?? theme.fonts.fontSize
      }

      // 行高
      if (style.lineHeight) style.lineHeight = theme.fonts.lineHeight

      // 文字色：长文本用正文色，标题用标题色（启发式：短文本 > 12px）
      if (typeof style.color === 'string' && typeof element.printText === 'string' &&
          element.printText.trim().length <= 3 && style.fontSize > 12) {
        style.color = theme.palette.textTitle
      }
    }

    // ---- 模块页眉/页脚 ----
    const footer = module.footer ?? module.pageFoot
    if (footer && footer.style) {
      footer.style.color = theme.palette.footer
      if (footer.style.fontFamily) footer.style.fontFamily = theme.fonts.fontFamily
    }
    const header = module.header ?? module.pageHeader
    if (header && header.style) {
      header.style.color = theme.palette.header
      if (header.style.fontFamily) header.style.fontFamily = theme.fonts.fontFamily
    }
  }

  return JSON.stringify(json)
}

/** 主题内容（设计和预览共用的中间态） */
export interface ThemeContent {
  printPage?: Record<string, any>
  printHorizontalRule?: Record<string, any>
  printVerticalRule?: Record<string, any>
  printModules?: any[]
  modules?: any[]
}

/**
 * 解析主题内容
 */
export function parseContent(content: string | ThemeContent): ThemeContent | null {
  try {
    if (typeof content === 'string') {
      return JSON.parse(content) as ThemeContent
    }
    return content as ThemeContent
  } catch {
    return null
  }
}

/**
 * 根据标识取主题（为 future 留扩展：用户的主题用 profile 配置号）
 */
export function getThemeById(id: string): Theme | undefined {
  return THEMES.find(item => item.id === id)
}

/**
 * 主题高速可视/方向：判断一个主题是否深色（preview 可据此切换 app 的 theme mode）
 */
export function isDark(theme?: Theme): boolean {
  return !!theme?.isDark || theme?.palette.background === '#0f172a'
}
