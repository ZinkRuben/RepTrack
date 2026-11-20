package hu.reptrack.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Az aktuális idő forrása (egy helyen cserélhető, pl. a mai nap meghatározásához). */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
