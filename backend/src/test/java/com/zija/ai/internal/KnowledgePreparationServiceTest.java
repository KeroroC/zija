package com.zija.ai.internal;

import com.zija.ai.internal.KnowledgeTextExtractor.TextUnit;
import com.zija.ai.internal.persistence.ClaimedKnowledgeSource;
import com.zija.ai.internal.persistence.KnowledgeChunkMapper;
import com.zija.ai.internal.persistence.KnowledgeSourceEntity;
import com.zija.ai.internal.persistence.KnowledgeSourceMapper;
import com.zija.file.FileApi;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgePreparationServiceTest {

    @Test
    void rejectsGarbageTextBeforeChunkingOrIndexing() {
        UUID sourceId = UUID.randomUUID();
        UUID householdId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T12:00:00+08:00");

        var mapper = mock(KnowledgeSourceMapper.class);
        var chunkMapper = mock(KnowledgeChunkMapper.class);
        var stateStore = mock(KnowledgeSourceStateStore.class);
        var fileApi = mock(FileApi.class);
        var extractor = mock(KnowledgeTextExtractor.class);
        var chunker = mock(KnowledgeChunker.class);
        var documentFactory = mock(KnowledgeChunkDocumentFactory.class);
        var vectorStore = mock(AiKnowledgeVectorStore.class);
        var scopeResolver = mock(KnowledgeScopeResolver.class);
        var service = new KnowledgePreparationService(
                mapper, chunkMapper, stateStore, fileApi, extractor,
                new KnowledgeTextQualityGate(), chunker, documentFactory, vectorStore, scopeResolver);

        var claim = new ClaimedKnowledgeSource();
        claim.setId(sourceId);
        claim.setProcessingVersion(1);
        when(mapper.claimDue(any(), any(), anyInt())).thenReturn(List.of(claim));

        var source = new KnowledgeSourceEntity();
        source.setId(sourceId);
        source.setHouseholdId(householdId);
        source.setFileId(fileId);
        source.setMountType(FileApi.MOUNT_HOUSEHOLD);
        source.setMountId(householdId);
        source.setStatus(KnowledgeSourceStates.STATUS_PROCESSING);
        source.setProcessingVersion(1);
        when(mapper.selectById(sourceId)).thenReturn(source);

        var attachment = new FileApi.AttachmentInfo(
                fileId, householdId, "说明书.pdf", "application/pdf", 100L,
                FileApi.MOUNT_HOUSEHOLD, householdId, now, null);
        when(fileApi.findAttachment(householdId, fileId)).thenReturn(Optional.of(attachment));
        when(fileApi.readContent(householdId, fileId)).thenReturn(Optional.of(new byte[]{1, 2, 3}));
        when(extractor.extract(eq("application/pdf"), any(byte[].class))).thenReturn(List.of(new TextUnit(
                "靪羮忊䉯夠巃㜨崯唻彿髦鲲閔艊䉣菬䯋踵鑫薶墮崯桭俋䅡姪鰱鲶羮梪鲲閔忞夃棾艊諤䎋點濕䯖㛽鮪懲羮頌㛬鄫䄕㜁梪忲謀",
                2,
                null)));

        service.prepareDue(now);

        verify(stateStore).markFailed(
                eq(sourceId), eq(1), eq(now),
                eq(KnowledgeSourceStates.FAILURE_TEXT_NOT_EXTRACTABLE),
                contains("字体编码映射异常"));
        verifyNoInteractions(chunker, documentFactory, vectorStore, scopeResolver);
    }
}
