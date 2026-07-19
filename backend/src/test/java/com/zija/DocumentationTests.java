package com.zija;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class DocumentationTests {

    private final ApplicationModules modules =
            ApplicationModules.of(ZijaApplication.class);

    @Test
    void writesModuleCanvases() {
        new Documenter(modules).writeModuleCanvases();
    }
}
