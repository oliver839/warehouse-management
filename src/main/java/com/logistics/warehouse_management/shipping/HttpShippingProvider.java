package com.logistics.warehouse_management.shipping;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@ConditionalOnProperty(name = "shipping.provider", havingValue = "http")
public class HttpShippingProvider implements ShippingProvider {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String apiUrl;
    private final String apiKey;
    private final String apiSecret;
    private final String tenant;

    public HttpShippingProvider(ObjectMapper objectMapper,
                                @Value("${shipping.api-url}") String apiUrl,
                                @Value("${shipping.api-key}") String apiKey,
                                @Value("${shipping.api-secret}") String apiSecret,
                                @Value("${shipping.tenant}") String tenant) {
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.tenant = tenant;
    }

    @Override
    public ShipmentResponse createShipment(ShipmentRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            HttpResponse<String> response = send("/shipments", "POST", json, "application/json");
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Versanddienstleister antwortete mit HTTP " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), ShipmentResponse.class);
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Versanddienstleister ist nicht erreichbar", exception);
        }
    }

    @Override
    public byte[] getLabel(String shipmentId) {
        try {
            HttpResponse<byte[]> response = sendBytes("/shipments/" + shipmentId + "/label");
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("Versandlabel konnte nicht geladen werden");
            return response.body();
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Versanddienstleister ist nicht erreichbar", exception);
        }
    }

    @Override
    public ShippingStatusResponse getStatus(String shipmentId) {
        try {
            HttpResponse<String> response = send("/shipments/" + shipmentId, "GET", null, null);
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("Versandstatus konnte nicht geladen werden");
            return objectMapper.readValue(response.body(), ShippingStatusResponse.class);
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Versanddienstleister ist nicht erreichbar", exception);
        }
    }

    private HttpResponse<String> send(String path, String method, String body, String contentType) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiUrl + path))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((apiKey + ":" + apiSecret).getBytes(StandardCharsets.UTF_8)))
                .header("X-Tenant", tenant);
        if ("POST".equals(method)) builder.header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body));
        else builder.GET();
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> sendBytes(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + path))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((apiKey + ":" + apiSecret).getBytes(StandardCharsets.UTF_8)))
                .header("X-Tenant", tenant).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
}