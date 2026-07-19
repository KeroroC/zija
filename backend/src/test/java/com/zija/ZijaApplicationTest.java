package com.zija;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

class ZijaApplicationTest {

    @Test
    void applicationDeclaresSpringBootEntryPoint() {
        assertThat(ZijaApplication.class)
                .hasAnnotation(SpringBootApplication.class);
    }
}
