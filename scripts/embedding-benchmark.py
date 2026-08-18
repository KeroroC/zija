#!/usr/bin/env python3
"""Compare local Ollama embedding models on a small synthetic Chinese corpus."""

from __future__ import annotations

import argparse
import json
import math
import platform
import statistics
import subprocess
import threading
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone


DOCUMENTS = [
    ("coffee-machine", "咖啡机使用说明：首次使用前加入清水并运行清洗程序。萃取后立即清空咖啡渣盒，每周清洁冲煮头。"),
    ("filter-stock", "滤纸库存记录：三号储物柜有两盒锥形滤纸，每盒一百张。低于一盒时提醒补充。"),
    ("winter-clothes", "冬季衣物收纳说明：羽绒服放在主卧衣柜上层，使用透气收纳袋，不要长期压缩存放。"),
    ("first-aid", "家庭急救箱清单：创可贴、纱布、碘伏和退热贴放在玄关抽屉。药品使用前检查有效期。"),
    ("air-purifier", "空气净化器维护手册：滤芯位于机器背面，指示灯变红后更换。新滤芯型号为 AP-220。"),
    ("rice-lot", "大米批次记录：五公斤装大米批次 RICE-2026-04，保质期至 2027 年四月，存放在厨房下柜。"),
    ("toolbox", "工具箱位置说明：螺丝刀、卷尺和六角扳手在阳台右侧工具箱，工具箱标签为 TOOL-01。"),
    ("bicycle", "自行车保养记录：后轮胎压建议保持在 45 PSI，每三个月检查链条并补充润滑油。"),
]

QUERIES = [
    ("咖啡机的咖啡渣盒多久清洁一次？", {"coffee-machine"}),
    ("锥形滤纸还有多少，放在哪里？", {"filter-stock"}),
    ("羽绒服应该怎样收纳？", {"winter-clothes"}),
    ("退热贴在家里的什么位置？", {"first-aid"}),
    ("空气净化器要换什么型号的滤芯？", {"air-purifier"}),
    ("RICE-2026-04 批次什么时候到期？", {"rice-lot"}),
    ("六角扳手放在哪里？", {"toolbox"}),
    ("自行车后轮建议打多少胎压？", {"bicycle"}),
]


class OllamaRssMonitor:
    def __init__(self) -> None:
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._run, daemon=True)
        self.peak_bytes: int | None = None

    def start(self) -> None:
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        self._thread.join(timeout=2)

    def _run(self) -> None:
        while not self._stop.is_set():
            rss = self._ollama_rss()
            if rss is not None:
                self.peak_bytes = max(self.peak_bytes or 0, rss)
            self._stop.wait(0.1)

    @staticmethod
    def _ollama_rss() -> int | None:
        try:
            result = subprocess.run(
                ["ps", "-axo", "pid=,rss=,command="],
                check=True,
                capture_output=True,
                text=True,
            )
        except (OSError, subprocess.SubprocessError):
            return None
        total_kib = 0
        found = False
        for line in result.stdout.splitlines():
            fields = line.strip().split(None, 2)
            if len(fields) < 3 or "ollama" not in fields[2].lower():
                continue
            try:
                total_kib += int(fields[1])
                found = True
            except ValueError:
                continue
        return total_kib * 1024 if found else None


def embed(base_url: str, model: str, text: str) -> tuple[list[float], float]:
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}/api/embed",
        data=json.dumps({"model": model, "input": text}).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            payload = json.load(response)
    except (OSError, urllib.error.URLError, json.JSONDecodeError) as exc:
        raise RuntimeError(f"embedding request failed for {model}: {exc}") from exc
    elapsed_ms = (time.perf_counter() - started) * 1000
    vectors = payload.get("embeddings")
    if vectors is None and payload.get("embedding") is not None:
        vectors = [payload["embedding"]]
    if not vectors or not isinstance(vectors[0], list):
        raise RuntimeError(f"Ollama returned no embedding for {model}: {payload}")
    return [float(value) for value in vectors[0]], elapsed_ms


def cosine(left: list[float], right: list[float]) -> float:
    numerator = sum(a * b for a, b in zip(left, right))
    left_norm = math.sqrt(sum(value * value for value in left))
    right_norm = math.sqrt(sum(value * value for value in right))
    return numerator / (left_norm * right_norm) if left_norm and right_norm else 0.0


def percentile(values: list[float], fraction: float) -> float:
    if not values:
        return 0.0
    if len(values) == 1:
        return values[0]
    return statistics.quantiles(values, n=100, method="inclusive")[max(0, int(fraction * 100) - 1)]


def benchmark_model(base_url: str, model: str, top_k: int) -> dict[str, object]:
    monitor = OllamaRssMonitor()
    latencies: list[float] = []
    monitor.start()
    try:
        document_vectors: list[list[float]] = []
        for _, document in DOCUMENTS:
            vector, elapsed_ms = embed(base_url, model, document)
            document_vectors.append(vector)
            latencies.append(elapsed_ms)

        hits = 0
        for query, relevant_ids in QUERIES:
            query_vector, elapsed_ms = embed(base_url, model, query)
            latencies.append(elapsed_ms)
            ranked = sorted(
                enumerate(document_vectors),
                key=lambda pair: cosine(query_vector, pair[1]),
                reverse=True,
            )[:top_k]
            retrieved_ids = {DOCUMENTS[index][0] for index, _ in ranked}
            if retrieved_ids.intersection(relevant_ids):
                hits += 1
    finally:
        monitor.stop()

    return {
        "model": model,
        "dimensions": len(document_vectors[0]),
        "recallAt5": hits / len(QUERIES),
        "queryCount": len(QUERIES),
        "p95LatencyMs": round(percentile(latencies, 0.95), 2),
        "peakOllamaRssBytes": monitor.peak_bytes,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:11434")
    parser.add_argument(
        "--models",
        default="nomic-embed-text:latest,qwen3-embedding:0.6b",
        help="comma-separated Ollama model names",
    )
    parser.add_argument("--top-k", type=int, default=5)
    parser.add_argument("--json-output", help="write the full report to this path")
    args = parser.parse_args()

    report: dict[str, object] = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "hardware": f"{platform.system()} {platform.machine()}",
        "baseUrl": args.base_url,
        "documentCount": len(DOCUMENTS),
        "queryCount": len(QUERIES),
        "topK": args.top_k,
        "models": [],
    }
    for model in (value.strip() for value in args.models.split(",")):
        if not model:
            continue
        result = benchmark_model(args.base_url, model, args.top_k)
        report["models"].append(result)
        rss = result["peakOllamaRssBytes"]
        rss_text = f"{rss / (1024 * 1024):.1f} MiB" if isinstance(rss, int) else "n/a"
        print(
            f"{model}: dimensions={result['dimensions']} "
            f"Recall@5={result['recallAt5']:.3f} "
            f"P95={result['p95LatencyMs']:.1f} ms RSS={rss_text}"
        )

    if args.json_output:
        with open(args.json_output, "w", encoding="utf-8") as output:
            json.dump(report, output, ensure_ascii=False, indent=2)
            output.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
