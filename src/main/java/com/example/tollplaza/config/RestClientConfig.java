package com.example.tollplaza.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Value("${app.openrouteservice.connect-timeout-ms:5000}")
    private long connectTimeoutMs;

    @Value("${app.openrouteservice.read-timeout-ms:10000}")
    private long readTimeoutMs;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        ClientHttpRequestInterceptor userAgentInterceptor = (request, body, execution) -> {
            request.getHeaders().add("User-Agent", "TollPlazaService/1.0");
            return execution.execute(request, body);
        };

        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .additionalInterceptors(userAgentInterceptor)
                .build();
    }
}
