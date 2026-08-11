/** 报表日期格式化工具 */

const pad = (n: number) => String(n).padStart(2, "0");

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
