/** 报表日期格式化 + 日期选择快捷项 */

const pad = (n: number) => String(n).padStart(2, "0");

export type DatePickerShortcut = {
  text: string
  value: () => Date
}

/**
 * 按日历月加减；目标月没有对应日时落到该月最后一天。
 * （例如 1/31 + 1 月 → 2/28 或 2/29，而不是溢出到 3/1。）
 */
export function addCalendarMonths(date: Date, months: number): Date {
  const day = date.getDate()
  const target = new Date(date.getFullYear(), date.getMonth() + months, 1)
  const lastDay = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate()
  target.setDate(Math.min(day, lastDay))
  return target
}

function startOfLocalDay(from: Date = new Date()): Date {
  return new Date(from.getFullYear(), from.getMonth(), from.getDate())
}

/** 购入日期 / 生产日期：相对今天的过去向快捷项 */
export const pastDateShortcuts: DatePickerShortcut[] = [
  { text: "今天", value: () => startOfLocalDay() },
  {
    text: "昨天",
    value: () => {
      const d = startOfLocalDay()
      d.setDate(d.getDate() - 1)
      return d
    },
  },
  {
    text: "一周前",
    value: () => {
      const d = startOfLocalDay()
      d.setDate(d.getDate() - 7)
      return d
    },
  },
]

/** 有效期至：相对今天的未来向快捷项 */
export const futureDateShortcuts: DatePickerShortcut[] = [
  { text: "3个月后", value: () => addCalendarMonths(startOfLocalDay(), 3) },
  { text: "6个月后", value: () => addCalendarMonths(startOfLocalDay(), 6) },
  { text: "1年后", value: () => addCalendarMonths(startOfLocalDay(), 12) },
]

/**
 * 日期时间列（业务时间等 OffsetDateTime 字符串）。
 * 按本地时区展示为 YYYY-MM-DD HH:mm，非法值原样返回。
 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/**
 * 纯日期列（到期日等 LocalDate 字符串）。
 * 已是 YYYY-MM-DD 则原样保留（避免时区导致日期偏移），其余兜底转换。
 */
export function formatDateOnly(value: string | null | undefined): string {
  if (!value) return "-";
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value;
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/**
 * 将 Element Plus 日期范围（"YYYY-MM-DD" 数组）转换为后端 OffsetDateTime 需要的
 * UTC 瞬时（ISO-8601，"2026-08-13T00:00:00.000Z"）。
 *
 * 含义：起始日 00:00 → 结束日 23:59:59.999（含结束日整天），均按浏览器本地时区解释后
 * 再转 UTC，与后端把业务时间存为 UTC 瞬时一致。
 * 参数为空数组或非法值 → 返回空对象（不传该过滤条件）。
 */
export function dateRangeToIsoBounds(
  range: string[] | null | undefined,
): { from?: string; to?: string } {
  if (!range || range.length !== 2) return {};
  const [fromDate, toDate] = range;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fromDate) || !/^\d{4}-\d{2}-\d{2}$/.test(toDate)) {
    return {};
  }
  const start = new Date(`${fromDate}T00:00:00`);
  const end = new Date(`${toDate}T23:59:59.999`);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return {};
  return { from: start.toISOString(), to: end.toISOString() };
}
