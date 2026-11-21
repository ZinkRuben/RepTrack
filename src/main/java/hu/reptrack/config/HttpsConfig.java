package hu.reptrack.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HTTPS esetén (server.ssl.enabled=true, pl. a docker profilban) egy második, sima HTTP-port, amely csak arra való,
 * hogy a http:// címre érkező kéréseket a SecurityConfig HTTPS-re irányítsa. Fejlesztéskor (SSL nélkül) nincs ilyen.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("server.ssl.enabled")
public class HttpsConfig {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpRedirectConnector(
            @Value("${reptrack.https.http-port:8080}") int httpPort) {
        return factory -> {
            Connector connector = new Connector();
            connector.setScheme("http");
            connector.setSecure(false);
            connector.setPort(httpPort);
            factory.addAdditionalConnectors(connector);
        };
    }
}
