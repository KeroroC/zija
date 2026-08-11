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
