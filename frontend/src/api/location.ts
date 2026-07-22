import { getJson, postJson, putJson, deleteJson } from './http'
import type { LocationTree, LocationInfo } from '../types/location'

export async function fetchLocationTree(): Promise<LocationTree> {
  return getJson<LocationTree>('/api/v1/locations/tree')
}

export async function fetchLocation(id: string): Promise<LocationInfo> {
  return getJson<LocationInfo>(`/api/v1/locations/${id}`)
}

export async function createLocation(data: {
  name: string
  parentId?: string
  sortOrder?: number
}): Promise<LocationInfo> {
  return postJson<LocationInfo>('/api/v1/locations', data)
}

export async function renameLocation(id: string, data: {
  name: string
  version: number
}): Promise<LocationInfo> {
  return putJson<LocationInfo>(`/api/v1/locations/${id}`, data)
}

export async function moveLocation(id: string, data: {
  parentId?: string
  sortOrder: number
  version: number
}): Promise<void> {
  return putJson<void>(`/api/v1/locations/${id}/position`, data)
}

export async function deleteLocation(id: string, version: number): Promise<void> {
  return deleteJson<void>(`/api/v1/locations/${id}`, { version })
}
