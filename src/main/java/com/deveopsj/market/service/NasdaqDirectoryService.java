package com.deveopsj.market.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.deveopsj.market.config.NasdaqDirectoryProperties;
import com.deveopsj.market.dto.KrxStockItem;

@Service
public class NasdaqDirectoryService {

    private static final Map<String, String> EXCHANGE_NAMES = Map.of(
            "A", "NYSE American",
            "N", "NYSE",
            "P", "NYSE Arca",
            "Z", "Cboe BZX",
            "V", "IEX");

    private final RestTemplate restTemplate;
    private final NasdaqDirectoryProperties properties;

    public NasdaqDirectoryService(@Qualifier("krxRestTemplate") RestTemplate restTemplate,
            NasdaqDirectoryProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public List<KrxStockItem> fetchAll() {
        try {
            String nasdaqListed = restTemplate.getForObject(properties.getNasdaqListedUrl(), String.class);
            String otherListed = restTemplate.getForObject(properties.getOtherListedUrl(), String.class);
            if (nasdaqListed == null || otherListed == null) {
                throw new IllegalStateException("Nasdaq 종목 파일 응답이 비어 있습니다.");
            }
            ArrayList<KrxStockItem> stocks = new ArrayList<>();
            stocks.addAll(parseNasdaqListed(nasdaqListed));
            stocks.addAll(parseOtherListed(otherListed));
            return stocks;
        } catch (RestClientException e) {
            throw new IllegalStateException("Nasdaq 종목 파일을 내려받지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    List<KrxStockItem> parseNasdaqListed(String content) {
        String[] lines = content.lines().toArray(String[]::new);
        if (lines.length == 0 || !lines[0].startsWith("Symbol|Security Name|")) {
            throw new IllegalStateException("Nasdaq 상장종목 파일 형식이 변경되었습니다.");
        }
        ArrayList<KrxStockItem> stocks = new ArrayList<>();
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            if (line.startsWith("File Creation Time")) {
                continue;
            }
            String[] columns = line.split("\\|", -1);
            if (columns.length < 7 || columns[0].isBlank() || "Y".equals(columns[3])) {
                continue;
            }
            stocks.add(toStock(columns[0], columns[1], "NASDAQ", "Y".equals(columns[6])));
        }
        return stocks;
    }

    List<KrxStockItem> parseOtherListed(String content) {
        String[] lines = content.lines().toArray(String[]::new);
        if (lines.length == 0 || !lines[0].startsWith("ACT Symbol|Security Name|")) {
            throw new IllegalStateException("미국 타 거래소 종목 파일 형식이 변경되었습니다.");
        }
        ArrayList<KrxStockItem> stocks = new ArrayList<>();
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            if (line.startsWith("File Creation Time")) {
                continue;
            }
            String[] columns = line.split("\\|", -1);
            if (columns.length < 8 || "Y".equals(columns[6])) {
                continue;
            }
            String symbol = columns[7].isBlank() ? columns[0] : columns[7];
            if (symbol.isBlank()) {
                continue;
            }
            String exchange = EXCHANGE_NAMES.getOrDefault(columns[2], columns[2]);
            stocks.add(toStock(symbol, columns[1], exchange, "Y".equals(columns[4])));
        }
        return stocks;
    }

    private KrxStockItem toStock(String symbol, String name, String market, boolean etf) {
        String assetClass = etf ? "ETF" : "STOCK";
        return new KrxStockItem(
                null, symbol, name, name, null, null,
                market, assetClass, assetClass, null);
    }
}
