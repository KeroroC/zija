# Embedding 本地基准

这是模型选择的手动基准，不是 CI 门禁，也不是全系统性能测试。样本是人工编写的家庭库存与附件文本，
不包含真实家庭资料。

## 运行

确保 Ollama 已运行并已拉取两个模型：

```bash
ollama pull nomic-embed-text:latest
ollama pull qwen3-embedding:0.6b
python3 scripts/embedding-benchmark.py --json-output /tmp/zija-embedding-benchmark.json
```

脚本会通过 `/api/embed` 测量每个模型的向量维度、`Recall@5`、单条请求 P95 延迟和本机 Ollama 进程
RSS 峰值。远程 Ollama 地址可用 `--base-url` 覆盖；远程进程不在本机时，RSS 会显示为 `n/a`。

相对门槛是：Qwen3 的 `Recall@5` 不低于 Nomic，峰值内存和 P95 延迟不超过 Nomic 的 2 倍。报告应
同时记录机器、模型和样本数量，便于后续模型切换复核。
