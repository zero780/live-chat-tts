package com.comext.livechattts.adapter.in.http;

import com.comext.livechattts.adapter.out.live.LocalTestLiveChatClient;
import com.comext.livechattts.application.port.in.ConnectionUseCase;
import com.comext.livechattts.application.port.in.SpeechSettingsUseCase;
import com.comext.livechattts.application.port.in.StatusUseCase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class LocalHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ConnectionUseCase connection;
    private final SpeechSettingsUseCase settings;
    private final StatusUseCase status;
    private final LocalTestLiveChatClient testClient;
    private final String apiToken;

    public LocalHttpServer(int port, ConnectionUseCase connection, SpeechSettingsUseCase settings, StatusUseCase status, LocalTestLiveChatClient testClient, String apiToken) throws IOException {
        this.connection = connection; this.settings = settings; this.status = status; this.testClient = testClient; this.apiToken = apiToken == null ? "" : apiToken;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/api", this::handle);
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
    }

    public void start() { server.start(); }
    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!authorized(exchange)) { send(exchange, 401, Map.of("error", "No autorizado")); return; }
            String path = exchange.getRequestURI().getPath(); String method = exchange.getRequestMethod();
            if ("OPTIONS".equals(method)) { exchange.sendResponseHeaders(204, -1); return; }
            if ("GET".equals(method) && "/api/health".equals(path)) { send(exchange, 200, Map.of("status", "UP")); return; }
            if ("GET".equals(method) && "/api/status".equals(path)) { send(exchange, 200, statusJson()); return; }
            if ("GET".equals(method) && "/api/voices".equals(path)) { send(exchange, 200, Map.of("voices", settings.voices().stream().map(v -> Map.of("id", v.id(), "displayName", v.displayName())).toList())); return; }
            if ("GET".equals(method) && "/api/settings".equals(path)) { send(exchange, 200, settingsJson(settings.settings())); return; }
            if ("PUT".equals(method) && "/api/settings".equals(path)) { updateSettings(exchange); return; }
            if ("POST".equals(method) && "/api/connect".equals(path)) { connection.connect(required(body(exchange), "username")); send(exchange, 200, connectionJson(connection.snapshot())); return; }
            if ("POST".equals(method) && "/api/disconnect".equals(path)) { connection.disconnect(); send(exchange, 200, connectionJson(connection.snapshot())); return; }
            if ("POST".equals(method) && "/api/test/messages".equals(path)) { publishTestMessage(exchange); return; }
            send(exchange, 404, Map.of("error", "Ruta no encontrada"));
        } catch (IllegalArgumentException exception) { send(exchange, 400, Map.of("error", exception.getMessage()));
        } catch (Exception exception) { send(exchange, 500, Map.of("error", "Error interno local")); }
    }

    private void updateSettings(HttpExchange exchange) throws IOException {
        String json = body(exchange); SpeechSettingsUseCase.Settings current = settings.settings();
        String voiceId = Optional.ofNullable(HttpJson.field(json, "voiceId")).orElse(current.voiceId());
        String output = Optional.ofNullable(HttpJson.field(json, "audioOutputId")).orElse(current.audioOutputId());
        Integer rate = HttpJson.integer(json, "speechRate");
        send(exchange, 200, settingsJson(settings.update(voiceId, rate == null ? current.speechRate() : rate, output)));
    }
    private void publishTestMessage(HttpExchange exchange) throws IOException {
        if (testClient == null) throw new IllegalArgumentException("La inyeccion de prueba solo esta disponible con LIVE_SOURCE=LOCAL_TEST");
        String json = body(exchange); boolean accepted = testClient.publish(required(json, "author"), required(json, "text"));
        if (!accepted) {
            send(exchange, 409, Map.of("error", "Conecta primero la sesi\u00f3n local antes de enviar una prueba."));
            return;
        }
        send(exchange, 202, Map.of("accepted", true));
    }
    private boolean authorized(HttpExchange exchange) { String supplied = exchange.getRequestHeaders().getFirst("X-Local-Api-Token"); return apiToken.isBlank() || (supplied != null && MessageDigest.isEqual(apiToken.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))); }
    private String body(HttpExchange exchange) throws IOException { String type = Optional.ofNullable(exchange.getRequestHeaders().getFirst("Content-Type")).orElse(""); if (!type.toLowerCase(java.util.Locale.ROOT).startsWith("application/json")) throw new IllegalArgumentException("Content-Type debe ser application/json"); byte[] bytes = exchange.getRequestBody().readNBytes(4_097); if (bytes.length > 4_096) throw new IllegalArgumentException("Cuerpo demasiado grande"); return new String(bytes, StandardCharsets.UTF_8); }
    private String required(String json, String field) { String value = HttpJson.field(json, field); if (value == null || value.isBlank()) throw new IllegalArgumentException("Falta " + field); return value; }
    private Map<String, Object> statusJson() { StatusUseCase.RuntimeStatus current = status.status(); Map<String, Object> map = new LinkedHashMap<>(); map.put("connection", connectionJson(current.connection())); map.put("speaking", current.speaking()); map.put("queueDepth", current.queueDepth()); map.put("acceptedMessages", current.acceptedMessages()); map.put("droppedMessages", current.droppedMessages()); map.put("rejectedMessages", current.rejectedMessages()); map.put("lastError", current.lastError()); map.put("lastErrorAt", current.lastErrorAt()); map.put("messages", current.messages().stream().map(message -> Map.of("id", message.id(), "author", message.author(), "text", message.text(), "receivedAt", message.receivedAt(), "state", message.state())).toList()); return map; }
    private Map<String, Object> connectionJson(ConnectionUseCase.ConnectionSnapshot snapshot) { return Map.of("state", snapshot.state().name(), "username", snapshot.username(), "detail", snapshot.detail()); }
    private Map<String, Object> settingsJson(SpeechSettingsUseCase.Settings current) { return Map.of("voiceId", current.voiceId(), "speechRate", current.speechRate(), "audioOutputId", current.audioOutputId(), "audioOutputs", settings.audioOutputs().stream().map(output -> Map.of("id", output.id(), "displayName", output.displayName())).toList()); }
    private void send(HttpExchange exchange, int status, Map<String, ?> payload) throws IOException { byte[] response = HttpJson.object(payload).getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8"); exchange.getResponseHeaders().set("Cache-Control", "no-store"); exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff"); exchange.getResponseHeaders().set("X-Frame-Options", "DENY"); exchange.sendResponseHeaders(status, response.length); exchange.getResponseBody().write(response); exchange.close(); }
    @Override public void close() { server.stop(1); }
}
