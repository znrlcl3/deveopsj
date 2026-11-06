package com.deveopsj.market.namuh;

import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

/** Public master, not an account API. Official m_new_stock.h: 237 bytes per record. */
@Component
public class NamuhStockCatalog {
    private static final int RECORD_SIZE = 237;
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private Map<String, Stock> cached = Map.of();
    private Instant expiresAt = Instant.EPOCH;
    private Instant retryAfter = Instant.EPOCH;
    private String lastFailure;
    public record Stock(String symbol, String name, String market, String assetClass) {}

    public synchronized Optional<Stock> find(String symbol) {
        if (!symbol.matches("[0-9A-Z]{6}")) return Optional.empty();
        if (Instant.now().isBefore(retryAfter)) throw new IllegalStateException(lastFailure);
        if (!Instant.now().isBefore(expiresAt)) {
            try { cached = parse(download()); }
            catch (IllegalStateException e) {
                lastFailure = e.getMessage(); retryAfter = Instant.now().plusSeconds(60); throw e;
            }
            expiresAt = Instant.now().plusSeconds(21600);
        }
        return Optional.ofNullable(cached.get(symbol));
    }
    private byte[] download() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create("https://www.nhplug.com/instruments/m_new_stock.mst").toURL().openConnection();
            connection.setConnectTimeout(5000); connection.setReadTimeout(10000); connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != 200) throw new java.io.IOException();
            try (var stream = connection.getInputStream()) {
                byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw new java.io.IOException();
                return bytes;
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("나무 공식 종목 목록을 불러오지 못했습니다. 잠시 후 잔고를 다시 조회하세요.");
        } finally { if (connection != null) connection.disconnect(); }
    }
    static Map<String, Stock> parse(byte[] bytes) {
        if (bytes.length == 0 || bytes.length % RECORD_SIZE != 0)
            throw new IllegalStateException("나무 종목 목록의 파일 구조가 변경되었습니다. 자동 연결을 중단했습니다.");
        Map<String, Stock> stocks = new HashMap<>();
        for (int offset = 0; offset < bytes.length; offset += RECORD_SIZE) {
            if (bytes[offset + 236] != '\n') throw new IllegalStateException("나무 종목 목록의 행 구분을 확인할 수 없습니다.");
            String symbol = field(bytes, offset, 6);
            String marketCode = field(bytes, offset + 6, 1);
            if (!Set.of("1", "4").contains(marketCode)) continue;
            if (!symbol.matches("[0-9A-Z]{6}")) throw new IllegalStateException("나무 종목 코드 형식을 확인할 수 없습니다.");
            String name = field(bytes, offset + 7, 41);
            if (name.startsWith("*") || name.startsWith("#")) name = name.substring(1).strip();
            if (name.isBlank()) throw new IllegalStateException("나무 종목명이 비어 있습니다.");
            boolean etf = "1".equals(marketCode) && bytes[offset + 165] == '8';
            var stock = new Stock(symbol, name, etf ? "ETF" : ("1".equals(marketCode) ? "KOSPI" : "KOSDAQ"), etf ? "ETF" : "STOCK");
            if (stocks.putIfAbsent(symbol, stock) != null) throw new IllegalStateException("나무 공식 목록에 중복 종목 코드가 있습니다.");
        }
        if (stocks.isEmpty()) throw new IllegalStateException("나무 공식 종목 목록이 비어 있습니다.");
        return Map.copyOf(stocks);
    }
    private static String field(byte[] bytes, int offset, int length) {
        try {
            return Charset.forName("MS949").newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, offset, length)).toString().strip();
        } catch (CharacterCodingException e) { throw new IllegalStateException("나무 종목 목록의 문자 인코딩을 확인할 수 없습니다."); }
    }
}
