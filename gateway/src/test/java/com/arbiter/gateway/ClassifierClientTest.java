package com.arbiter.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ClassifierClientTest {
    @Test
    void rejectsComplexityOutsideTheContractRange() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ClassificationResult(
                1.1, "general", "test", false, false));
    }
}
