package com.zija.ai.internal;

import com.zija.ai.internal.KnowledgeTextExtractor.TextUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeTextQualityGateTest {

    private final KnowledgeTextQualityGate gate = new KnowledgeTextQualityGate();

    @Test
    void rejectsLongCjkTextWithMalformedMappingCharacteristics() {
        var units = List.of(new TextUnit(
                "靪羮忊䉯夠巃㜨崯唻彿髦鲲閔艊䉣菬䯋踵鑫薶墮崯桭俋䅡姪鰱鲶羮梪鲲閔忞夃棾艊"
                        + "諤䎋點濕䯖㛽鮪懲羮頌㛬鄫䄕㜁梪忲謀",
                2,
                null));

        var result = gate.validate("application/pdf", units);

        assertThat(result.accepted()).isFalse();
        assertThat(result.failureMessage()).contains("字体编码映射异常");
    }

    @Test
    void acceptsNormalChineseManualText() {
        var units = List.of(new TextUnit(
                "使用说明\n请按说明书操作，设置频道和音量。每次使用前检查电池电量。",
                1,
                null));

        assertThat(gate.validate("application/pdf", units).accepted()).isTrue();
    }

    @Test
    void doesNotRejectEnglishOrShortChineseText() {
        assertThat(gate.validate("application/pdf", List.of(new TextUnit("DUAL BAND PROFESSIONAL RADIO", 1, null))).accepted())
                .isTrue();
        assertThat(gate.validate("application/pdf", List.of(new TextUnit("森海克斯 8800", 1, null))).accepted())
                .isTrue();
        assertThat(gate.validate("text/plain", List.of(new TextUnit(
                "靪羮忊䉯夠巃㜨崯唻彿髦鲲閔艊䉣菬䯋踵鑫薶墮崯桭俋䅡姪鰱鲶羮梪鲲閔忞夃棾艊諤䎋點濕䯖㛽鮪懲羮頌㛬鄫䄕㜁梪忲謀",
                null,
                null))).accepted()).isTrue();
    }
}
