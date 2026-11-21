package hu.reptrack.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.reptrack.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * HTTPS-beállítás (a docker profil szerint): a sima HTTP-kérés HTTPS-re irányítódik, a gazdagépen látható portra.
 * Itt a nyilvános portok szándékosan eltérnek a konténer belső portjaitól (9080/9443 a 8080/8443 helyett).
 * A tényleges TLS-t (tanúsítvány, Tomcat) a konténer indítása ellenőrzi, a MockMvc nem indít szervert.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "server.ssl.enabled=true",
        "reptrack.https.http-port=8080",
        "reptrack.https.public-http-port=9080",
        "reptrack.https.public-https-port=9443",
        "reptrack.https.hsts=false"})
class HttpsRedirectTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;

    private static RequestPostProcessor http(int port) {
        return request -> {
            request.setScheme("http");
            request.setServerPort(port);
            return request;
        };
    }

    private static RequestPostProcessor https(int port) {
        return request -> {
            request.setScheme("https");
            request.setSecure(true);
            request.setServerPort(port);
            return request;
        };
    }

    @Test
    void plainHttpOnThePublicPortIsRedirectedToTheHttpsPort() throws Exception {
        mvc.perform(get("/exercises").queryParam("source", "OWN").with(http(9080)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://localhost:9443/exercises?source=OWN"));
    }

    @Test
    void plainHttpOnTheContainerPortIsRedirectedToo() throws Exception {
        mvc.perform(get("/login").with(http(8080)))
                .andExpect(redirectedUrl("https://localhost:9443/login"));
    }

    @Test
    void httpsIsServedWithoutHstsForTheSelfSignedSetup() throws Exception {
        mvc.perform(get("/login").with(https(9443)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }
}
