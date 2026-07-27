// ==================== Common / Shared ====================

/** Generic paginated response wrapper. */
export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}
