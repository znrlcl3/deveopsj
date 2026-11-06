package com.deveopsj.market.namuh;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Fixed read-only API allowlist. Credentials/URLs/response bodies must never be logged. */
@Component
public class NamuhApiClient {
    static final String BASE = "https://api.nhplug.com:8443";
    static final Set<String> READ_PATHS = Set.of("/n2/acctinfo", "/krstock/inquiry/v1/balance",
            "/gbstock/inquiry/v1/balance", "/common/inquiry/v1/totalTransaction");
    private final HttpClient http;
    private final ObjectMapper json;
    private long lastCall;

    @org.springframework.beans.factory.annotation.Autowired
    public NamuhApiClient(ObjectMapper json) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build(), json);
    }
    NamuhApiClient(HttpClient http, ObjectMapper json) { this.http = http; this.json = json; }

    public record Token(String value, long expiresIn) {
        @Override public String toString() { return "Token[REDACTED]"; }
    }
    public Token token(String key, String secret) {
        String query = "appkey=" + encode(key) + "&appsecretkey=" + encode(secret)
                + "&grant_type=client_credentials&scope=oob";
        var request = HttpRequest.newBuilder(URI.create(BASE + "/oauth2/token?" + query))
                .timeout(Duration.ofSeconds(12)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        JsonNode body = parse(send(request).body());
        String token = body.path("access_token").asText("");
        long expiry = body.path("expires_in").asLong(0);
        if (token.isBlank() || token.length() > 2500 || expiry <= 60 || expiry > 86400 * 7)
            throw new IllegalStateException("나무 인증 응답을 확인하지 못했습니다. 키와 서비스 신청 상태를 확인하세요.");
        return new Token(token, expiry);
    }

    public List<JsonNode> rows(String token, String path, Map<String, String> input, String block) {
        if (!READ_PATHS.contains(path)) throw new IllegalArgumentException("허용되지 않은 나무 조회입니다.");
        List<JsonNode> rows = new ArrayList<>(); Set<String> seen = new HashSet<>(); String cursor = "";
        for (int page = 0; page < 30; page++) {
            var builder = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(12))
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("Input_0", input))));
            if (!cursor.isBlank()) builder.header("cts", cursor).header("cts_flag", "Y");
            var response = send(builder.build()); JsonNode body = parse(response.body());
            validateResponse(body, block);
            JsonNode list = body.path(block);
            if (list.isArray()) list.forEach(rows::add);
            if (rows.size() > 10000) throw new IllegalStateException("조회 건수가 너무 많습니다. 조회 범위를 줄여 주세요.");
            String code = body.path("message").path("msg_code").asText(body.path("rsp_cd").asText(""));
            boolean more = "Y".equalsIgnoreCase(response.headers().firstValue("cts_flag").orElse(""))
                    || Set.of("00165", "00218").contains(code);
            if (!more) return List.copyOf(rows);
            cursor = response.headers().firstValue("cts").orElse("");
            if (cursor.isBlank() || !seen.add(cursor))
                throw new IllegalStateException("나무 연속조회가 완료되지 않았습니다. 일부 내역은 반영하지 않았습니다.");
        }
        throw new IllegalStateException("나무 조회 페이지 한도를 초과했습니다. 일부 내역은 반영하지 않았습니다.");
    }

    static void validateResponse(JsonNode body, String block) {
        String message = body.path("rsp_msg").asText(body.path("message").path("usr_msg").asText(""));
        String level = body.path("message").path("msg_lv_code").asText("");
        String code = body.path("message").path("msg_code").asText(body.path("rsp_cd").asText(""));
        boolean complete = message.contains("완료") || message.contains("정상")
                || message.contains("내역이 없습니다") || message.contains("자료가 없습니다")
                || message.contains("조회내역이 없습니다") || Set.of("00165", "00218").contains(code);
        if (Set.of("E", "F").contains(level) || !complete
                || (body.has(block) && !body.path(block).isArray())) {
            throw new IllegalStateException("나무 조회 응답을 확인할 수 없습니다. 계좌와 신청 상태를 확인하세요.");
        }
    }

    private synchronized HttpResponse<String> send(HttpRequest request) {
        try {
            long wait = 250 - (System.nanoTime() - lastCall) / 1_000_000;
            if (wait > 0) Thread.sleep(wait);
            lastCall = System.nanoTime();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 401) throw new TokenExpired();
            if (response.statusCode() == 429) throw new IllegalStateException("나무 조회 요청이 많습니다. 잠시 후 다시 조회하세요.");
            if (response.statusCode() != 200) throw new IllegalStateException("나무 연결에 실패했습니다. 잠시 후 다시 확인하세요.");
            if (response.body().length() > 5_000_000) throw new IllegalStateException("나무 응답 크기를 초과했습니다.");
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("나무 조회가 중단되었습니다.");
        } catch (java.io.IOException e) { throw new IllegalStateException("나무 서버에 연결하지 못했습니다. 잠시 후 다시 확인하세요."); }
    }
    private JsonNode parse(String body) {
        try { return json.readTree(body); }
        catch (RuntimeException e) { throw new IllegalStateException("나무 응답 형식을 확인할 수 없습니다."); }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    public static class TokenExpired extends IllegalStateException {
        public TokenExpired() { super("나무 인증이 만료되었습니다. 다시 조회하거나 연결정보를 갱신하세요."); }
    }
}
