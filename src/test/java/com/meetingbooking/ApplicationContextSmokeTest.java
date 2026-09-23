package com.meetingbooking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ApplicationContextSmokeTest {
    @Test
    void applicationStartsWithTheConfiguredJpaMappingsAndSecurityBeans() {
        // Context boot validates entity mappings, repository JPQL, and bean wiring.
    }
}
