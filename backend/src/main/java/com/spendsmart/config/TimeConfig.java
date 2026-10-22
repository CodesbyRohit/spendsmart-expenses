package com.spendsmart.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /**
     * Injected wherever "today" matters so date-boundary behaviour is testable. {@code app.clock.fixed} (an ISO
     * instant) pins time for automated tests; it is never set in normal profiles.
     */
    @Bean
    Clock clock(@Value("${app.clock.fixed:}") String fixed, @Value("${app.clock.zone:}") String zone) {
        ZoneId zoneId = zone == null || zone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(zone);
        if (fixed != null && !fixed.isBlank()) {
            return Clock.fixed(Instant.parse(fixed), zoneId);
        }
        return Clock.system(zoneId);
    }
}
