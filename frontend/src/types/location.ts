export interface LocationNode {
  id: string
  parentId: string | null
  name: string
  sortOrder: number
  everReferenced: boolean
  version: number
  children: LocationNode[]
}

export interface LocationTree {
  roots: LocationNode[]
}

export interface LocationInfo {
  id: string
  householdId: string
  parentId: string | null
  name: string
  sortOrder: number
  everReferenced: boolean
  version: number
}
