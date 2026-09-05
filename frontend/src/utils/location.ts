import type { LocationNode } from '../types/location'

export function findLocationNode(nodes: LocationNode[], id: string): LocationNode | null {
  for (const n of nodes) {
    if (n.id === id) return n
    const child = findLocationNode(n.children, id)
    if (child) return child
  }
  return null
}

// 与后端 findTree 排序一致：sort_order 升序，其次 id
export function compareLocationNodes(a: LocationNode, b: LocationNode): number {
  if (a.sortOrder !== b.sortOrder) return a.sortOrder - b.sortOrder
  return a.id < b.id ? -1 : a.id > b.id ? 1 : 0
}

export function flattenLocationChoices(
  nodes: LocationNode[],
  prefix = "",
): Array<{ value: string; label: string }> {
  const result: Array<{ value: string; label: string }> = []
  for (const node of nodes) {
    const label = prefix ? `${prefix} / ${node.name}` : node.name
    result.push({ value: node.id, label })
    if (node.children?.length) {
      result.push(...flattenLocationChoices(node.children, label))
    }
  }
  return result
}
