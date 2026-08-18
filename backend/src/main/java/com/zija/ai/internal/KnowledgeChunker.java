package com.zija.ai.internal;

import com.zija.ai.internal.KnowledgeTextExtractor.TextUnit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识来源文本分块：把文本单元切成可嵌入、可定位的片段。
 *
 * <p>分块以段落边界优先（尽量在换行处切断），单个超长段落按字符数硬切；
 * 每块记录相对所在单元的 {@code charStart/charEnd}，配合页码/章节构成回答依据定位。
 * 算法确定且无随机性，便于测试与恢复重建。</p>
 */
@Component
class KnowledgeChunker {

    /** 单块目标字符数。 */
    static final int CHUNK_CHAR_TARGET = 1200;

    /** 一个可嵌入分块：正文 + 定位信息（页码/章节 + 单元内字符区间）。 */
    record Chunk(String text, Integer pageNumber, String sectionPath, int charStart, int charEnd) {
    }

    List<Chunk> chunk(List<TextUnit> units) {
        List<Chunk> chunks = new ArrayList<>();
        for (TextUnit unit : units) {
            String text = unit.text();
            int pos = 0;
            while (pos < text.length()) {
                int end = Math.min(pos + CHUNK_CHAR_TARGET, text.length());
                if (end < text.length()) {
                    // 段落边界优先：在 (pos, end] 内找最后一个换行，避免把段落劈成两半
                    int newline = text.lastIndexOf('\n', end - 1);
                    if (newline > pos) {
                        end = newline + 1;
                    }
                }
                String chunkText = text.substring(pos, end).strip();
                if (!chunkText.isEmpty()) {
                    chunks.add(new Chunk(chunkText, unit.pageNumber(), unit.sectionPath(), pos, end));
                }
                pos = end;
            }
        }
        return chunks;
    }
}
