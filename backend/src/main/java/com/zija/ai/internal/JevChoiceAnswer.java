package com.zija.ai.internal;

import java.util.Map;

/** Jev {@code choice} 原语的结构化结果。 */
record JevChoiceAnswer(String choice, double confidence, Map<String, Double> probabilities) {
}
