package com.comext.livechattts.bootstrap;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Black-box smoke test for the packaged local API. It intentionally uses the LOCAL_TEST source only. */
final class SelfTest {
    private SelfTest() { }

    static void run(int port) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        URI base = URI.create("http://127.0.0.1:" + port + "/api/");
        assertStatus(client.send(HttpRequest.newBuilder(base.resolve("health")).GET().build(), HttpResponse.BodyHandlers.ofString()), 200, "health");
        assertStatus(client.send(json(base.resolve("connect"), "{\"username\":\"local_test\"}"), HttpResponse.BodyHandlers.ofString()), 200, "connect");
        assertStatus(client.send(json(base.resolve("test/messages"), "{\"author\":\"SmokeTest\",\"text\":\"Prueba local de voz\"}"), HttpResponse.BodyHandlers.ofString()), 202, "message");
        HttpResponse<String> status = client.send(HttpRequest.newBuilder(base.resolve("status")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertStatus(status, 200, "status");
        if (!status.body().contains("\"acceptedMessages\":1")) throw new IllegalStateException("La cola no acepto el mensaje de prueba");
        System.out.println("SELF-TEST OK: API local, conexion de prueba y cola verificados.");
    }

    private static HttpRequest json(URI uri, String body) { return HttpRequest.newBuilder(uri).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(); }
    private static void assertStatus(HttpResponse<String> response, int expected, String step) { if (response.statusCode() != expected) throw new IllegalStateException(step + " devolvio HTTP " + response.statusCode() + ": " + response.body()); }
}
