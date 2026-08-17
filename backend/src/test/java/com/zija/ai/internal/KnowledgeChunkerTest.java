package com.zija.ai.internal;

import com.zija.ai.internal.KnowledgeChunker.Chunk;
import com.zija.ai.internal.KnowledgeTextExtractor.TextUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeChunkerTest {

    private final KnowledgeChunker chunker = new KnowledgeChunker();

    @Test
    void keepsShortUnitAsSingleChunkWithOffsets() {
        List<Chunk> chunks = chunker.chunk(List.of(new TextUnit("一段较短的正文", 3, "保养")));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().text()).isEqualTo("一段较短的正文");
        assertThat(chunks.getFirst().pageNumber()).isEqualTo(3);
        assertThat(chunks.getFirst().sectionPath()).isEqualTo("保养");
        assertThat(chunks.getFirst().charStart()).isZero();
        assertThat(chunks.getFirst().charEnd()).isEqualTo("一段较短的正文".length());
    }

    @Test
    void splitsLongTextAtParagraphBoundariesWithAccumulatingOffsets() {
        String line = "这是一行超过目标长度的正文。".repeat(200);
        String unit = (line + "\n\n" + line).repeat(1);
        List<Chunk> chunks = chunker.chunk(List.of(new TextUnit(unit, 1, null)));

        assertThat(chunks.size()).isGreaterThan(1);
        Chunk previous = null;
        for (Chunk chunk : chunks) {
            assertThat(chunk.text()).isNotEmpty();
            assertThat(chunk.charEnd()).isGreaterThan(chunk.charStart());
            if (previous != null) {
                assertThat(chunk.charStart()).isEqualTo(previous.charEnd());
            }
            previous = chunk;
        }
        assertThat(previous.charEnd()).isLessThanOrEqualTo(unit.length());
    }

    @Test
    void keepsPageAndSectionPerUnit() {
        var chunks = chunker.chunk(List.of(
                new TextUnit("第一单元", 1, "章节A"),
                new TextUnit("第二单元", 2, "章节B")));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).pageNumber()).isEqualTo(1);
        assertThat(chunks.get(0).sectionPath()).isEqualTo("章节A");
        assertThat(chunks.get(1).pageNumber()).isEqualTo(2);
        assertThat(chunks.get(1).sectionPath()).isEqualTo("章节B");
    }

    @Test
    void ignoresBlankUnits() {
        List<Chunk> chunks = chunker.chunk(List.of(new TextUnit("   \n  ", null, null)));

        assertThat(chunks).isEmpty();
    }
}
