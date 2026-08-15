import { ensureCsrf, getCookie, ApiError, getJson, patchJson } from './http'
import type { ApiProblem } from '../types/system'

export interface UploadedFile {
  id: string
  storageKey: string
  originalFilename: string
  detectedMediaType: string
  byteSize: number
  sha256: string
  url: string
  version: number
}

export interface Attachment {
  id: string
  name: string
  mediaType: string
  byteSize: number
  mountType: string
  mountId: string
  createdAt: string
  url: string
}

export interface AttachmentPage {
  items: Attachment[]
  total: number
  page: number
  pageSize: number
}

export async function listAttachments(params?: {
  page?: number
  pageSize?: number
}): Promise<AttachmentPage> {
  const query = new URLSearchParams()
  if (params?.page) query.set('page', String(params.page))
  if (params?.pageSize) query.set('pageSize', String(params.pageSize))
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return getJson<AttachmentPage>(`/api/v1/files${suffix}`)
}

export async function uploadHouseholdAttachment(file: File): Promise<UploadedFile> {
  await ensureCsrf()
  const formData = new FormData()
  formData.append('file', file)

  const cookieToken = getCookie('XSRF-TOKEN')
  const headers: Record<string, string> = {}
  if (cookieToken) {
    headers['X-XSRF-TOKEN'] = cookieToken
  }

  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? ''
  const response = await fetch(`${baseUrl}/api/v1/files`, {
    method: 'POST',
    credentials: 'same-origin',
    headers,
    body: formData
  })

  if (response.ok) {
    return response.json() as Promise<UploadedFile>
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

export async function renameAttachment(id: string, name: string): Promise<Attachment> {
  return patchJson<Attachment>(`/api/v1/files/${id}`, { name })
}

export async function uploadItemCover(itemId: string, file: File, version: number): Promise<UploadedFile> {
  await ensureCsrf()
  const formData = new FormData()
  formData.append('file', file)

  const cookieToken = getCookie('XSRF-TOKEN')
  const headers: Record<string, string> = {}
  if (cookieToken) {
    headers['X-XSRF-TOKEN'] = cookieToken
  }

  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? ''
  const response = await fetch(`${baseUrl}/api/v1/items/${itemId}/cover?version=${version}`, {
    method: 'POST',
    credentials: 'same-origin',
    headers,
    body: formData
  })

  if (response.ok) {
    return response.json() as Promise<UploadedFile>
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

export async function removeItemCover(itemId: string, version: number): Promise<void> {
  const { deleteJson } = await import('./http')
  return deleteJson(`/api/v1/items/${itemId}/cover`, { version })
}
