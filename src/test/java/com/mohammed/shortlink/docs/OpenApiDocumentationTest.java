package com.mohammed.shortlink.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    private JsonNode document;

    @BeforeEach
    void readSpecification() throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        document = mapper.readTree(json);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/openapi.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document));
    }

    @Test
    void describesTheThreeApiPathsAndTheirStatuses() {
        assertThat(document.path("info").path("title").asText()).isEqualTo("Shortlink Service API");
        assertThat(document.path("servers").get(0).path("url").asText()).isEqualTo("/");
        JsonNode paths = document.path("paths");
        assertThat(paths.size()).isEqualTo(3);
        assertThat(paths.path("/api/links").path("post").path("responses").has("201")).isTrue();
        assertThat(paths.path("/api/links").path("post").path("responses").has("400")).isTrue();
        assertThat(paths.path("/api/links/{code}/stats").path("get").path("responses").has("200")).isTrue();
        assertThat(paths.path("/api/links/{code}/stats").path("get").path("responses").has("404")).isTrue();
        JsonNode redirect = paths.path("/s/{code}").path("get").path("responses");
        assertThat(redirect.has("302") && redirect.has("404") && redirect.has("410")).isTrue();
        assertThat(redirect.has("200")).isFalse();
    }

    @Test
    void includesRequestExamplesAndRequiredUrlConstraints() {
        JsonNode request = document.path("paths").path("/api/links").path("post")
                .path("requestBody").path("content").path("application/json");
        assertThat(request.path("examples").path("Permanent link").path("value").path("originalUrl").asText())
                .isEqualTo("https://example.com/articles/java");
        assertThat(request.path("examples").path("Expiring link").path("value").has("expiresAt")).isTrue();
        JsonNode schema = document.path("components").path("schemas").path("CreateLinkRequest");
        assertThat(schema.path("required").toString()).contains("originalUrl");
        assertThat(schema.path("properties").path("originalUrl").path("maxLength").asInt()).isEqualTo(2048);
        assertThat(schema.path("properties").path("expiresAt").path("nullable").asBoolean()).isTrue();
    }

    @Test
    void documentsRedirectHeadersAndEmptyBody() {
        JsonNode response = document.path("paths").path("/s/{code}").path("get").path("responses").path("302");
        assertThat(response.path("headers").has("Location")).isTrue();
        assertThat(response.path("headers").has("Cache-Control")).isTrue();
        assertThat(response.path("content").isMissingNode() || response.path("content").isEmpty()).isTrue();
    }

    @Test
    void documentsAnalyticsAndProblemDetailExamples() {
        JsonNode properties = document.path("components").path("schemas").path("LinkAnalyticsResponse").path("properties");
        assertThat(properties.has("clickCount") && properties.has("lastClickedAt") && properties.has("expired")).isTrue();
        assertThat(properties.path("lastClickedAt").path("nullable").asBoolean()).isTrue();
        JsonNode gone = document.path("paths").path("/s/{code}").path("get").path("responses").path("410")
                .path("content").path("application/problem+json");
        assertThat(gone.path("example").path("status").asInt()).isEqualTo(410);
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void servesContainerLivenessWithoutAddingItToBusinessApiDocs() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("UP"));
        assertThat(document.path("paths").has("/health")).isFalse();
    }
}
