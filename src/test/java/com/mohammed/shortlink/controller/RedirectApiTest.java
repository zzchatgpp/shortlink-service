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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RedirectApiTest.FixedClockConfig.class)
class RedirectApiTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private ShortLinkRepository repository;
    @Autowired private ObjectMapper mapper;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock redirectTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @BeforeEach
    void clearLinks() {
        repository.deleteAll();
    }

    @Test
    void redirectsWithoutExpiryAndPreservesQueryAndFragment() throws Exception {
        String url = "https://example.com/page?q=java%20spring&lang=en#intro";
        ShortLink link = repository.saveAndFlush(new ShortLink(url, null));
        mvc.perform(get("/s/{code}", Base62.encode(link.getId())))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", url))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));
        ShortLink clicked = repository.findById(link.getId()).orElseThrow();
        assertThat(clicked.getClickCount()).isEqualTo(1);
        assertThat(clicked.getLastClickedAt()).isEqualTo(NOW);
    }

    @Test
    void redirectsUntilExpiry() throws Exception {
        ShortLink link = repository.saveAndFlush(new ShortLink("https://example.com", NOW.plusSeconds(1)));
        mvc.perform(get("/s/{code}", Base62.encode(link.getId())))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2030-01-01T00:00:00Z", "2029-12-31T23:59:59Z"})
    void expiredAndExactlyExpiringLinksReturn410(String expiry) throws Exception {
        ShortLink link = repository.saveAndFlush(new ShortLink("https://example.com", Instant.parse(expiry)));
        mvc.perform(get("/s/{code}", Base62.encode(link.getId())))
                .andExpect(status().isGone())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.title").value("Link expired"))
                .andExpect(jsonPath("$.detail").value("This short link has expired"));
        ShortLink expired = repository.findById(link.getId()).orElseThrow();
        assertThat(expired.getClickCount()).isZero();
        assertThat(expired.getLastClickedAt()).isNull();
    }

    @Test
    void unknownValidCodeReturns404() throws Exception {
        mvc.perform(get("/s/{code}", Base62.encode(Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.title").value("Link not found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "01", "abc-", "abc_", "aZl8N0y58M8", "ZZZZZZZZZZZ", "ZZZZZZZZZZZZ"})
    void invalidAndOverflowingCodesReturn404(String code) throws Exception {
        mvc.perform(get("/s/{code}", code))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.detail").value("No link exists for this short code"));
    }

    @Test
    void headRequestReturnsRedirectHeadersWithoutBody() throws Exception {
        ShortLink link = repository.saveAndFlush(new ShortLink("https://example.com", null));
        mvc.perform(head("/s/{code}", Base62.encode(link.getId())))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"))
                .andExpect(content().string(""));
        ShortLink untouched = repository.findById(link.getId()).orElseThrow();
        assertThat(untouched.getClickCount()).isZero();
        assertThat(untouched.getLastClickedAt()).isNull();
    }

    @Test
    void createdShortUrlCanBeResolved() throws Exception {
        String body = mvc.perform(post("/api/links").contentType("application/json")
                .content("{\"originalUrl\":\"https://example.com/created\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String code = mapper.readTree(body).get("shortCode").asText();
        mvc.perform(get("/s/{code}", code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/created"));
    }

    @Test
    void unicodePathsAreEncodedInLocationHeader() throws Exception {
        ShortLink link = repository.saveAndFlush(new ShortLink("https://example.com/café", null));
        mvc.perform(get("/s/{code}", Base62.encode(link.getId())))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/caf%C3%A9"));
    }

    @Test
    void repeatedGetRequestsCountEveryRedirect() throws Exception {
        ShortLink link = repository.saveAndFlush(new ShortLink("https://example.com", null));
        String code = Base62.encode(link.getId());
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/s/{code}", code)).andExpect(status().isFound());
        }
        ShortLink clicked = repository.findById(link.getId()).orElseThrow();
        assertThat(clicked.getClickCount()).isEqualTo(3);
        assertThat(clicked.getLastClickedAt()).isEqualTo(NOW);
    }
}
