package com.zija.household.internal;

import com.zija.ZijaSessionInvalidator;
import com.zija.household.internal.exception.HouseholdAlreadyInitializedException;
import com.zija.household.internal.persistence.HouseholdMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class HouseholdBootstrapIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired HouseholdService householdService;
    @Autowired HouseholdMapper householdMapper;

    @MockitoBean
    ZijaSessionInvalidator sessionInvalidator;

    @Test
    void singleBootstrapSucceedsAndConcurrentFails() {
        householdService.bootstrap(new HouseholdService.BootstrapCommand(
                "我的家", "owner", "Passw0rd!", "所有者", null));

        assertThatThrownBy(() -> householdService.bootstrap(new HouseholdService.BootstrapCommand(
                "第二个", "other", "Passw0rd!", "Other", null)))
                .isInstanceOf(HouseholdAlreadyInitializedException.class);

        assertThat(householdMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.emptyWrapper())).isEqualTo(1);
    }
}
