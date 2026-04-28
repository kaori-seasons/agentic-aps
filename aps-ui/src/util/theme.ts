import useCommonStore from '@/store/common'
import { darkblueStyle, darkredStyle, simpleStyle } from '@/const/theme'

const THEME_MAP: Record<string, { theme: string; params: any }> = {
  [simpleStyle.theme]: simpleStyle,
  [darkblueStyle.theme]: darkblueStyle,
  [darkredStyle.theme]: darkredStyle,
}

export const themeOptions = [
  { label: '深蓝主题', value: 'darkblue' },
  { label: '简约主题', value: 'simple' },
  { label: '深红主题', value: 'darkred' },
]

export function resolveThemeConfig(themeName?: string) {
  return THEME_MAP[themeName || ''] || darkblueStyle
}

export function applyThemeByName(themeName?: string) {
  const commonStore = useCommonStore()
  const themeConfig = resolveThemeConfig(themeName)
  commonStore.SET_THEME_NAME(themeConfig.theme)
  commonStore.SET_THEME(themeConfig.params)
}
