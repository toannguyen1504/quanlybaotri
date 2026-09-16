package com.example.quanlybaotri.config;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    @Bean RestClient.Builder restClientBuilder() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().requestFactory(factory).requestInterceptor(RestClientConfig::execute);
    }

    private static ClientHttpResponse execute(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        if (request.getMethod() != HttpMethod.GET) return execution.execute(request, body);
        for (int attempt = 0; ; attempt++) {
            try {
                ClientHttpResponse response = execution.execute(request, body);
                if (attempt < 2 && response.getStatusCode().is5xxServerError()) {
                    response.close();
                    continue;
                }
                return response;
            } catch (IOException failure) {
                if (attempt == 2) throw failure;
            }
        }
    }
}
