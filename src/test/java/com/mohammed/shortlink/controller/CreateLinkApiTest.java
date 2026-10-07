package com.mohammed.shortlink.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mohammed.shortlink.entity.ShortLink;
import com.mohammed.shortlink.repository.ShortLinkRepository;
import com.mohammed.shortlink.util.Base62;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.base-url=https://short.example/")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CreateLinkApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private ShortLinkRepository repository;

    @BeforeEach
    void clearLinks() {
        repository.deleteAll();
    }

    @Test
    void createsAndPersistsLinkWithExpiry() throws Exception {
        String url = "https://example.com/article?source=demo#section";
        String expiry = Instant.now().plusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
        MvcResult result = mvc.perform(post("/api/links").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("originalUrl", url, "expiresAt", expiry))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalUrl").value(url))
                .andExpect(jsonPath("$.expiresAt").value(expiry))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();
        JsonNode response = mapper.readTree(result.getResponse().getContentAsString());
        long id = response.get("id").asLong();
        String code = response.get("shortCode").asText();
        assertThat(Base62.decode(code)).isEqualTo(id);
        assertThat(response.get("shortUrl").asText()).isEqualTo("https://short.example/s/" + code);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(response.get("shortUrl").asText());
        ShortLink persisted = repository.findById(id).orElseThrow();
        assertThat(persisted.getOriginalUrl()).isEqualTo(url);
        assertThat(persisted.getExpiresAt()).isEqualTo(Instant.parse(expiry));
        assertThat(persisted.getClickCount()).isZero();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void omittedExpiryMeansNoExpiryAndDuplicateUrlsReceiveDifferentCodes() throws Exception {
        String body = "{\"originalUrl\":\"http://example.com\"}";
        JsonNode first = mapper.readTree(mvc.perform(post("/api/links").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        JsonNode second = mapper.readTree(mvc.perform(post("/api/links").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(first.get("expiresAt").isNull()).isTrue();
        assertThat(first.get("shortCode").asText()).isNotEqualTo(second.get("shortCode").asText());
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void explicitNullExpiryMeansNoExpiry() throws Exception {
        mvc.perform(post("/api/links").contentType("application/json")
                .content("{\"originalUrl\":\"https://example.com\",\"expiresAt\":null}"))
                .andExpect(status().isCreated());
        assertThat(repository.findAll().get(0).getExpiresAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "example.com", "/relative", "ftp://example.com", "javascript:alert(1)",
            "https://", "https://example.com/a b", "https://user:pass@example.com", "https://example.com:70000",
            "https://example.com:0", "https://example.com/%zz"})
    void invalidUrlsReturn400WithoutWriting(String url) throws Exception {
        mvc.perform(post("/api/links").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("originalUrl", url))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.originalUrl").isNotEmpty());
        assertThat(repository.count()).isZero();
    }

    @Test
    void missingUrlReturns400WithoutWriting() throws Exception {
        mvc.perform(post("/api/links").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.originalUrl").isNotEmpty());
        assertThat(repository.count()).isZero();
    }

    @Test
    void oversizedUrlReturns400WithoutWriting() throws Exception {
        mvc.perform(post("/api/links").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("originalUrl", "https://example.com/" + "a".repeat(2048)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.originalUrl").isNotEmpty());
        assertThat(repository.count()).isZero();
    }

    @Test
    void pastExpiryReturns400WithoutWriting() throws Exception {
        mvc.perform(post("/api/links").contentType("application/json")
                .content("{\"originalUrl\":\"https://example.com\",\"expiresAt\":\"2000-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.expiresAt").isNotEmpty());
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "[]", "{\"originalUrl\":\"https://example.com\",\"expiresAt\":\"bad-date\"}"})
    void malformedBodiesReturn400WithoutWriting(String body) throws Exception {
        mvc.perform(post("/api/links").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Invalid request body"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void expiryBeyondMysqlRangeReturns400WithoutWriting() throws Exception {
        mvc.perform(post("/api/links").contentType("application/json")
                .content("{\"originalUrl\":\"https://example.com\",\"expiresAt\":\"+10000-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.expiresAt").value("expiresAt must not be later than 9999-12-31T23:59:59Z"));
        assertThat(repository.count()).isZero();
    }
}
