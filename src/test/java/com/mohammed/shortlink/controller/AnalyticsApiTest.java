package com.mohammed.shortlink.controller;

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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.base-url=https://short.example")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AnalyticsApiTest.FixedClockConfig.class)
class AnalyticsApiTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private ShortLinkRepository repository;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock analyticsTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @BeforeEach
    void clearLinks() {
        repository.deleteAll();
    }

    @Test
    void newLinkReportsZeroClicksAndNoLastClick() throws Exception {
        ShortLink link = repository.saveAndFlush(new ShortLink("https://example.com/new", null));
        String code = Base62.encode(link.getId());
        mvc.perform(get("/api/links/{code}/stats", code))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(link.getId()))
                .andExpect(jsonPath("$.shortCode").value(code))
                .andExpect(jsonPath("$.shortUrl").value("https://short.example/s/" + code))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/new"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isEmpty())
                .andExpect(jsonPath("$.clickCount").value(0))
                .andExpect(jsonPath("$.lastClickedAt").isEmpty())
                .andExpect(jsonPath("$.expired").value(false));
    }

    @Test
    void createRedirectAndStatsFlowReportsClicksWithoutCountingStatsReads() throws Exception {
        String response = mvc.perform(post("/api/links").contentType("application/json")
                .content("{\"originalUrl\":\"https://example.com/demo\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String code = mapper.readTree(response).get("shortCode").asText();
        long id = mapper.readTree(response).get("id").asLong();
        for (int i = 0; i < 2; i++) mvc.perform(get("/s/{code}", code)).andExpect(status().isFound());
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/links/{code}/stats", code))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clickCount").value(2))
                    .andExpect(jsonPath("$.lastClickedAt").value(NOW.toString()));
        }
        mvc.perform(head("/s/{code}", code)).andExpect(status().isFound());
        assertThat(repository.findById(id).orElseThrow().getClickCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2030-01-01T00:00:00Z", "2029-12-31T23:59:59Z"})
    void expiredLinksStillExposeStatsButDoNotRedirect(String expiry) throws Exception {
        ShortLink link = repository.saveAndFlush(new ShortLink("https://example.com/expired", Instant.parse(expiry)));
        String code = Base62.encode(link.getId());
        mvc.perform(get("/api/links/{code}/stats", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value(expiry))
                .andExpect(jsonPath("$.expired").value(true))
                .andExpect(jsonPath("$.clickCount").value(0));
        mvc.perform(get("/s/{code}", code)).andExpect(status().isGone());
        assertThat(repository.findById(link.getId()).orElseThrow().getClickCount()).isZero();
    }

    @Test
    void futureExpiryIsReportedAsActive() throws Exception {
        Instant expiry = NOW.plusSeconds(60);
        ShortLink link = repository.saveAndFlush(new ShortLink("https://example.com/active", expiry));
        mvc.perform(get("/api/links/{code}/stats", Base62.encode(link.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value(expiry.toString()))
                .andExpect(jsonPath("$.expired").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "01", "abc-", "aZl8N0y58M8", "aZl8N0y58M7"})
    void unknownMalformedAndOverflowingCodesReturn404(String code) throws Exception {
        mvc.perform(get("/api/links/{code}/stats", code))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Link not found"));
    }
}
