import { getJson, postJson } from './http'
import type { SearchResult, ReportPage } from '../types/reporting'

const BASE = '/api/v1/reporting'

/** 全局搜索 */
export async function searchReporting(
  q: string,
  limitPerGroup = 5,
): Promise<SearchResult> {
  const qs = new URLSearchParams({ q, limitPerGroup: String(limitPerGroup) })
  return getJson<SearchResult>(`${BASE}/search?${qs}`)
}

/** 报表查询 */
export async function getReport<T>(
  reportKey: string,
  params: Record<string, string | number | undefined>,
): Promise<ReportPage<T>> {
  const qs = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== '') qs.set(k, String(v))
  }
  return getJson<ReportPage<T>>(`${BASE}/reports/${reportKey}?${qs}`)
}

/** 构建 CSV 导出 URL（用于浏览器直接下载） */
export function buildExportUrl(
  reportKey: string,
  params: Record<string, string | undefined>,
): string {
  const qs = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== '') qs.set(k, v)
  }
  // 添加 CSRF token 从 cookie
  const csrf = document.cookie.match(/XSRF-TOKEN=([^;]+)/)?.[1]
  if (csrf) qs.set('_csrf', csrf)
  return `${BASE}/exports/${reportKey}?${qs}`
}

/** 触发投影重建 */
export async function rebuildProjection(
  householdId: string,
): Promise<void> {
  await postJson<void>(`${BASE}/projection/rebuild`, { householdId })
}
