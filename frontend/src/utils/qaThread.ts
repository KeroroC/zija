import type {
  HouseholdFactAnswer,
  QaAnswerScope,
  QaQuestionScope,
} from "../types/ai";

const STORAGE_KEY = "zija.qa.thread";
const VERSION = 1;

export interface QaThreadTurn {
  question: string;
  answerScope: QaAnswerScope;
  answer: HouseholdFactAnswer;
  confirmedScopes: QaQuestionScope[];
}

export interface QaThreadSnapshot {
  turns: QaThreadTurn[];
  draft: string;
}

interface StoredSnapshot extends QaThreadSnapshot {
  version: number;
}

export function loadQaThread(): QaThreadSnapshot {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return emptySnapshot();
    const parsed: unknown = JSON.parse(raw);
    return normalizeSnapshot(parsed) ?? emptySnapshot();
  } catch {
    return emptySnapshot();
  }
}

export function saveQaThread(snapshot: QaThreadSnapshot): void {
  const turns = Array.isArray(snapshot.turns) ? snapshot.turns : [];
  const draft = typeof snapshot.draft === "string" ? snapshot.draft : "";
  if (turns.length === 0 && draft.trim() === "") {
    sessionStorage.removeItem(STORAGE_KEY);
    return;
  }
  const stored: StoredSnapshot = { version: VERSION, turns, draft };
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
}

export function clearQaThread(): void {
  sessionStorage.removeItem(STORAGE_KEY);
}

function emptySnapshot(): QaThreadSnapshot {
  return { turns: [], draft: "" };
}

function normalizeSnapshot(value: unknown): QaThreadSnapshot | null {
  if (!isRecord(value) || value.version !== VERSION) return null;
  if (!Array.isArray(value.turns)) return null;
  const draft = typeof value.draft === "string" ? value.draft : "";
  const turns: QaThreadTurn[] = [];
  for (const turn of value.turns) {
    const normalized = normalizeTurn(turn);
    if (!normalized) return null;
    turns.push(normalized);
  }
  return { turns, draft };
}

function normalizeTurn(value: unknown): QaThreadTurn | null {
  if (!isRecord(value)) return null;
  if (typeof value.question !== "string" || !value.question.trim()) return null;
  if (typeof value.answerScope !== "string") return null;
  if (!isRecord(value.answer)) return null;
  const confirmedScopes = Array.isArray(value.confirmedScopes)
    ? value.confirmedScopes.filter(isQuestionScope)
    : [];
  return {
    question: value.question,
    answerScope: value.answerScope as QaAnswerScope,
    answer: value.answer as unknown as HouseholdFactAnswer,
    confirmedScopes,
  };
}

function isQuestionScope(value: unknown): value is QaQuestionScope {
  if (!isRecord(value)) return false;
  return (value.type === "ITEM" || value.type === "LOT" || value.type === "LOCATION")
    && typeof value.id === "string"
    && value.id.length > 0;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
