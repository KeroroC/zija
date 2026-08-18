package com.zija.ai.internal;

import com.zija.ai.internal.KnowledgeTextExtractor.TextUnit;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rejects extracted text that is structurally present but very unlikely to be
 * readable Chinese. This protects the vector store from malformed PDF
 * ToUnicode/CMap output; OCR is intentionally outside this gate.
 */
@Component
class KnowledgeTextQualityGate {

    private static final int MIN_CJK_CHARACTERS = 40;
    private static final double MIN_COMMON_CJK_RATIO = 0.05d;

    // A compact high-frequency vocabulary used only to detect obvious CJK
    // glyph-to-Unicode corruption, not to validate document semantics.
    private static final String COMMON_CJK = "的了一是在不有我人这中大来上个到说为和地要于出会可也你对生能而过下自之后发年作里用道行所然家种事成方多经么去法学如都同现当没动面起看定天分还进好小部其些主样理心她本前开但因只从想实日军者意无力它与长把机十民第公此已工使情明性知全三点正外将两文间各重并物名向题问位次表级解设应关次口却真流接该万手少门六候结放边色脸亲快着呢拿放必位场案非音习风收带类未始存供若命增取书指众转更近千每根据信步最反认论处识接计非象清传切场片容直谁较调像房";

    Validation validate(String mediaType, List<TextUnit> units) {
        if (!KnowledgeSourceFormat.PDF.mediaType().equals(mediaType) || units == null || units.isEmpty()) {
            return Validation.ok();
        }
        int cjkCharacters = 0;
        int commonCjkCharacters = 0;
        for (TextUnit unit : units) {
            if (unit == null || unit.text() == null) {
                continue;
            }
            for (int codePoint : unit.text().codePoints().toArray()) {
                if (isCjk(codePoint)) {
                    cjkCharacters++;
                    if (COMMON_CJK.indexOf(codePoint) >= 0) {
                        commonCjkCharacters++;
                    }
                }
            }
        }
        if (cjkCharacters < MIN_CJK_CHARACTERS) {
            return Validation.ok();
        }
        double commonRatio = (double) commonCjkCharacters / cjkCharacters;
        if (commonRatio < MIN_COMMON_CJK_RATIO) {
            return new Validation(false,
                    "提取到的中文字符疑似乱码（常用汉字占比过低），可能是 PDF 字体编码映射异常");
        }
        return Validation.ok();
    }

    private static boolean isCjk(int codePoint) {
        return (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF);
    }

    public record Validation(boolean accepted, String failureMessage) {
        public static Validation ok() {
            return new Validation(true, null);
        }
    }
}
