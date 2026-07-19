package com.zija;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyAlignmentTests {

    @Test
    void usesSpringBootManagedTestcontainersTwo() {
        assertThat(DockerClientFactory.TESTCONTAINERS_VERSION)
                .startsWith("2.");
    }
}
