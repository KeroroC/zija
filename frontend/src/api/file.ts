import { ensureCsrf, getCookie, ApiError, getJson, patchJson, postJson, putJson, deleteJson } from './http'
import type { ApiProblem } from '../types/system'

export type MountType = 'HOUSEHOLD' | 'ITEM' | 'LOT'

export interface Attachment {
  id: string
  name: string
  mediaType: string
  byteSize: number
  mountType: MountType
  mountId: string
  createdAt: string
  deletedAt?: string
  url: string
}

export interface AttachmentPage {
  items: Attachment[]
  total: number
  page: number
  pageSize: number
}

/** 封面操作结果：附件信息 + 物品新版本号。 */
export interface CoverResult extends Attachment {
  version: number
}

/** 有资格被指定为封面的图片媒体类型（与后端 FileApi.COVER_MEDIA_TYPE_* 一致）。 */
export const COVER_IMAGE_TYPES: readonly string[] = ['image/jpeg', 'image/png', 'image/webp']

export interface AttachmentListResponse {
  items: Attachment[]
  total: number
}

export async function listAttachments(params?: {
  page?: number
  pageSize?: number
  mountType?: MountType | string
  mountId?: string
  q?: string
  recycled?: boolean
}): Promise<AttachmentPage> {
  const query = new URLSearchParams()
  if (params?.page) query.set('page', String(params.page))
  if (params?.pageSize) query.set('pageSize', String(params.pageSize))
  if (params?.mountType) query.set('mountType', params.mountType)
  if (params?.mountId) query.set('mountId', params.mountId)
  if (params?.q) query.set('q', params.q)
  if (params?.recycled) query.set('recycled', 'true')
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return getJson<AttachmentPage>(`/api/v1/files${suffix}`)
}

/** 列出物品上的未删除附件。 */
export async function listItemAttachments(itemId: string): Promise<Attachment[]> {
  const res = await getJson<AttachmentListResponse>(`/api/v1/items/${itemId}/attachments`)
  return res.items
}

/** 列出批次上的未删除附件。 */
export async function listLotAttachments(lotId: string): Promise<Attachment[]> {
  const res = await getJson<AttachmentListResponse>(`/api/v1/inventory/lots/${lotId}/attachments`)
  return res.items
}

/** 通用 multipart 上传（带 CSRF）。 */
async function uploadForm(path: string, file: File): Promise<Attachment> {
  await ensureCsrf()
  const formData = new FormData()
  formData.append('file', file)

  const cookieToken = getCookie('XSRF-TOKEN')
  const headers: Record<string, string> = {}
  if (cookieToken) {
    headers['X-XSRF-TOKEN'] = cookieToken
  }

  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? ''
  const response = await fetch(`${baseUrl}${path}`, {
    method: 'POST',
    credentials: 'same-origin',
    headers,
    body: formData
  })

  if (response.ok) {
    return response.json() as Promise<Attachment>
  }

  let problem: ApiProblem = {}
  try {
    problem = (await response.json()) as ApiProblem
  } catch {
    problem = {}
  }
  throw new ApiError(
    problem.title ?? 'Upload failed',
    problem.errorCode ?? 'http_error',
    response.status,
    problem.requestId ?? response.headers.get('X-Request-Id') ?? undefined
  )
}

export async function uploadHouseholdAttachment(file: File): Promise<Attachment> {
  return uploadForm('/api/v1/files', file)
}

export async function uploadItemAttachment(itemId: string, file: File): Promise<Attachment> {
  return uploadForm(`/api/v1/items/${itemId}/attachments`, file)
}

export async function uploadLotAttachment(lotId: string, file: File): Promise<Attachment> {
  return uploadForm(`/api/v1/inventory/lots/${lotId}/attachments`, file)
}

export async function renameAttachment(id: string, name: string): Promise<Attachment> {
  return patchJson<Attachment>(`/api/v1/files/${id}`, { name })
}

/** 删除附件：进入回收站（保留期内可恢复）。 */
export async function deleteAttachment(id: string): Promise<Attachment> {
  return deleteJson<Attachment>(`/api/v1/files/${id}`)
}

export async function restoreAttachment(id: string): Promise<Attachment> {
  return postJson<Attachment>(`/api/v1/files/${id}/restore`)
}

/** 永久删除回收站附件：跳过保留期，物理删除，不可恢复。 */
export async function purgeAttachment(id: string): Promise<{ id: string; purged: boolean }> {
  return deleteJson(`/api/v1/files/${id}?permanent=true`)
}

/** 改挂到家庭（当前家庭）。 */
export async function remountAttachmentToHousehold(id: string): Promise<Attachment> {
  return patchJson<Attachment>(`/api/v1/files/${id}/mount`, { mountType: 'HOUSEHOLD' })
}

/** 改挂到物品（catalog 入口）。 */
export async function remountAttachmentToItem(itemId: string, fileId: string): Promise<Attachment> {
  return patchJson<Attachment>(`/api/v1/items/${itemId}/attachments/${fileId}/mount`)
}

/** 改挂到批次（inventory 入口）。 */
export async function remountAttachmentToLot(lotId: string, fileId: string): Promise<Attachment> {
  return patchJson<Attachment>(`/api/v1/inventory/lots/${lotId}/attachments/${fileId}/mount`)
}

/** 上传并指定封面。换封面时 oldCoverAction: 'KEEP'（缺省）| 'RECYCLE'。 */
export async function uploadItemCover(
  itemId: string,
  file: File,
  version: number,
  oldCoverAction?: 'KEEP' | 'RECYCLE'
): Promise<CoverResult> {
  await ensureCsrf()
  const formData = new FormData()
  formData.append('file', file)

  const cookieToken = getCookie('XSRF-TOKEN')
  const headers: Record<string, string> = {}
  if (cookieToken) {
    headers['X-XSRF-TOKEN'] = cookieToken
  }

  const query = new URLSearchParams({ version: String(version) })
  if (oldCoverAction) query.set('oldCoverAction', oldCoverAction)

  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? ''
  const response = await fetch(`${baseUrl}/api/v1/items/${itemId}/cover?${query.toString()}`, {
    method: 'POST',
    credentials: 'same-origin',
    headers,
    body: formData
  })

  if (response.ok) {
    return response.json() as Promise<CoverResult>
  }

  let problem: ApiProblem = {}
  try {
    problem = (await response.json()) as ApiProblem
  } catch {
    problem = {}
  }
  throw new ApiError(
    problem.title ?? 'Upload failed',
    problem.errorCode ?? 'http_error',
    response.status,
    problem.requestId ?? response.headers.get('X-Request-Id') ?? undefined
  )
}

/** 把物品上已有的合格图片附件指定为封面。 */
export async function designateItemCover(
  itemId: string,
  fileId: string,
  version: number,
  oldCoverAction?: 'KEEP' | 'RECYCLE'
): Promise<CoverResult> {
  return putJson<CoverResult>(`/api/v1/items/${itemId}/cover`, {
    fileId,
    version,
    oldCoverAction
  })
}

/** 取消封面指定（附件仍留在物品上）。 */
export async function removeItemCover(itemId: string, version: number): Promise<void> {
  return deleteJson(`/api/v1/items/${itemId}/cover`, { version })
}
