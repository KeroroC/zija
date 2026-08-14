import type { MovementType } from "../types/inventory"

/** 库存流水类型：统一中文标签（与后端/首页「冲正」术语一致） */
export const MOVEMENT_TYPE_LABELS: Record<MovementType, string> = {
  INBOUND: "入库",
  CONSUME: "领用",
  LOSS: "报损",
  ADJUSTMENT: "调整",
  TRANSFER: "移位",
  REVERSAL: "冲正",
}

/** 筛选下拉选项 */
export const MOVEMENT_TYPE_OPTIONS: { value: MovementType; label: string }[] = [
  { value: "INBOUND", label: "入库" },
  { value: "CONSUME", label: "领用" },
  { value: "LOSS", label: "报损" },
  { value: "ADJUSTMENT", label: "调整" },
  { value: "TRANSFER", label: "移位" },
  { value: "REVERSAL", label: "冲正" },
]

export type MovementTagType = "success" | "primary" | "warning" | "danger" | "info"

/** el-tag type 映射：入库绿 / 领用主 / 报损红 / 调整与冲正黄 / 移位灰 */
const MOVEMENT_TAG_MAP: Record<MovementType, MovementTagType> = {
  INBOUND: "success",
  CONSUME: "primary",
  LOSS: "danger",
  ADJUSTMENT: "warning",
  TRANSFER: "info",
  REVERSAL: "warning",
}

export function movementTypeLabel(type: string): string {
  return MOVEMENT_TYPE_LABELS[type as MovementType] ?? type
}

export function movementTagType(type: string): MovementTagType {
  return MOVEMENT_TAG_MAP[type as MovementType] ?? "info"
}

/** 库存增减方向由流水类型推导的集合：领用/报损为扣减。 */
const DECREASE_TYPES: ReadonlySet<string> = new Set(["CONSUME", "LOSS"])

/**
 * 流水数量列的有符号渲染。
 * reporting_movement_flat.quantity_delta 落库的是正数幅度，增减方向需由类型推导：
 * 入库为加，领用/报损为减；移位/调整/冲正不是单纯增或减（或方向无法从类型推导），
 * 显示原数值不带强制符号。
 */
export function signedMovementQuantity(type: string, quantityDelta: number): string {
  if (DECREASE_TYPES.has(type)) return `-${quantityDelta}`
  if (type === "INBOUND") return `+${quantityDelta}`
  return String(quantityDelta)
}
