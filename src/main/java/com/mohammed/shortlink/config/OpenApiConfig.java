package com.mohammed.shortlink.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI shortlinkOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Shortlink Service API").version("1.0.0")
                        .description("""
                                Create HTTP/HTTPS short links with optional expiry, follow redirects,
                                and inspect click analytics. Codes use case-sensitive Base62.
                                Create a link first, then use its returned shortCode in other requests.
                                Expiry timestamps use ISO-8601 with a timezone. Omitting expiry creates
                                a permanent link. Analytics remains available after a link expires.
                                Each active GET redirect counts once; HEAD and analytics reads do not.
                                """))
                // Same-origin requests work locally and behind Railway's HTTPS proxy.
                .servers(List.of(new Server().url("/").description("Current application origin")));
    }
}
