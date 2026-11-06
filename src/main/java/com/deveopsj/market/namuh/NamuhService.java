package com.deveopsj.market.namuh;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

@Service @RequiredArgsConstructor
public class NamuhService {
    private final NamuhConnectionRepository connections;
    private final NamuhSecretCipher cipher;
    private final NamuhApiClient api;
    private final com.deveopsj.member.repository.MemberRepository members;

    public record Account(String number) {
        public String masked() { return "*******" + number.substring(Math.max(0, number.length() - 4)); }
        @Override public String toString() { return "Account[" + masked() + "]"; }
    }
    public record Balance(String symbol, String name, BigDecimal quantity, BigDecimal averagePrice,
            String currency, BigDecimal exchangeRate, String type) {}
    public record Transaction(String date, String actualDate, String symbol, String name, String description,
            BigDecimal quantity, BigDecimal price, BigDecimal amount, String currency,
            BigDecimal fee, BigDecimal tax) {}
    public boolean ready() { return cipher.ready(); }
    @Transactional(readOnly = true)
    public boolean connected(Long member) { return connections.existsById(member); }

    @Transactional
    public void connect(Long member, String key, String secret) {
        requireActive(member);
        if (!ready()) throw new IllegalStateException("서버의 나무 연결 설정이 준비되지 않았습니다. 관리자에게 문의하세요.");
        if (key == null || secret == null || key.isBlank() || secret.isBlank() || key.length() > 512 || secret.length() > 512)
            throw new IllegalArgumentException("APPKEY와 APP Secret을 확인해 주세요.");
        var token = api.token(key.trim(), secret.trim());
        accountsWithToken(token.value()); // Validate credential scope before replacing the old connection.
        var connection = connections.lockByMember(member).orElseGet(NamuhConnection::new);
        connection.setMemberId(member);
        connection.setEncryptedKey(cipher.encrypt(member, "key", key.trim()));
        connection.setEncryptedSecret(cipher.encrypt(member, "secret", secret.trim()));
        setToken(connection, token); connections.saveAndFlush(connection);
    }
    @Transactional
    public void disconnect(Long member) {
        connections.lockByMember(member).ifPresent(connections::delete);
    }
    @Transactional(noRollbackFor = IllegalStateException.class)
    public List<Account> accounts(Long member) {
        return accountRows(query(connection(member), "/n2/acctinfo", Map.of(), "Output_0"));
    }
    @Transactional(noRollbackFor = IllegalStateException.class)
    public List<Balance> balances(Long member, String account, String market) {
        var connection = connection(member);
        requireAccount(connection, account);
        List<JsonNode> result;
        if ("KR".equals(market)) {
            result = query(connection, "/krstock/inquiry/v1/balance", Map.of("act_no", account,
                    "bnc_bse_cd", "1", "ltg_aot_dit_cd", "9", "aet_bse", "2", "qut_dit_cd", "UNT", "aly_qut_cd", "2"), "Output_1");
        } else {
            String currency = currencyFor(market);
            result = query(connection, "/gbstock/inquiry/v1/balance", Map.of("act_no", account,
                    "qut_iqr_dit_cd", "9", "fc_sec_trd_nat_cd", market, "cur_cd", currency, "xns_dit_cd", "0"), "Output_1");
        }
        List<Balance> balances = new ArrayList<>();
        for (var row : result) {
            boolean kr = "KR".equals(market);
            BigDecimal quantity = number(row, kr ? "itg_bnc_qty" : "cns_bse_bnc_qty");
            if (quantity.signum() <= 0) continue;
            balances.add(new Balance(text(row, "iem_cd"), text(row, "iem_nm"), quantity,
                    number(row, kr ? "phs_pr" : "fc_phs_uit_pr"), kr ? "KRW" : text(row, "cur_cd"),
                    kr ? BigDecimal.ONE : number(row, "phs_xcg_rt"), text(row, kr ? "tp_cd_nm" : "cfd_lon_cd_nm")));
        }
        return List.copyOf(balances);
    }
    @Transactional(noRollbackFor = IllegalStateException.class)
    public List<Transaction> transactions(Long member, String account, YearMonth month, String market) {
        if (month == null || month.getYear() < 1900 || month.isAfter(YearMonth.now()))
            throw new IllegalArgumentException("현재 월 이전의 조회 월을 선택하세요.");
        if (!Set.of("KR", "FOREIGN").contains(market)) throw new IllegalArgumentException("조회 시장을 선택하세요.");
        var connection = connection(member); requireAccount(connection, account);
        var end = month.atEndOfMonth().isAfter(LocalDate.now()) ? LocalDate.now() : month.atEndOfMonth();
        var result = query(connection, "/common/inquiry/v1/totalTransaction", Map.of("act_no", account,
                "iqr_tp_cd", "1", "iqr_rge_cd", "1", "iqr_sta_dt", month.atDay(1).format(DateTimeFormatter.BASIC_ISO_DATE),
                "iqr_end_dt", end.format(DateTimeFormatter.BASIC_ISO_DATE), "iem_llf_cd", "KR".equals(market) ? "01" : "15", "act_trd_dtl_cd", "03"), "Output_0");
        return result.stream().map(row -> new Transaction(text(row, "trd_dt"), text(row, "ral_trd_dt"),
                text(row, "iem_cd"), text(row, "iem_nm"), text(row, "sps_cd_krl_anm"),
                number(row, "trd_qty"), number(row, "trd_uit_pr"), number(row, "trd_amt"),
                text(row, "cur_cd"), number(row, "trd_orn_fee"), number(row, "tax_sum"))).toList();
    }
    private NamuhConnection connection(Long member) {
        requireActive(member);
        return connections.lockByMember(member).orElseThrow(() -> new IllegalStateException("먼저 나무증권을 연결해 주세요."));
    }
    private void requireAccount(NamuhConnection connection, String account) {
        if (account == null || accountRows(query(connection, "/n2/acctinfo", Map.of(), "Output_0")).stream().noneMatch(a -> a.number().equals(account)))
            throw new IllegalArgumentException("본인에게 연결된 일반 계좌를 선택하세요.");
    }
    private List<Account> accountsWithToken(String token) {
        return accountRows(api.rows(token, "/n2/acctinfo", Map.of(), "Output_0"));
    }
    private List<Account> accountRows(List<JsonNode> rows) {
        return rows.stream()
                .filter(row -> "01".equals(text(row, "acct_type")))
                .map(row -> text(row, "acct_no")).filter(value -> value.matches("[0-9]{11}"))
                .distinct().map(Account::new).toList();
    }
    private List<JsonNode> query(NamuhConnection connection, String path, Map<String, String> input, String block) {
        try { return api.rows(token(connection), path, input, block); }
        catch (NamuhApiClient.TokenExpired expired) {
            refresh(connection);
            return api.rows(token(connection), path, input, block);
        }
    }
    private String token(NamuhConnection connection) {
        if (connection.getEncryptedToken() == null || connection.getTokenExpiresAt() == null
                || !Instant.now().isBefore(connection.getTokenExpiresAt().minusSeconds(60))) refresh(connection);
        return cipher.decrypt(connection.getMemberId(), "token", connection.getEncryptedToken());
    }
    private void refresh(NamuhConnection connection) {
        var member = connection.getMemberId();
        setToken(connection, api.token(cipher.decrypt(member, "key", connection.getEncryptedKey()),
                cipher.decrypt(member, "secret", connection.getEncryptedSecret())));
        connections.saveAndFlush(connection);
    }
    private void setToken(NamuhConnection connection, NamuhApiClient.Token token) {
        connection.setEncryptedToken(cipher.encrypt(connection.getMemberId(), "token", token.value()));
        connection.setTokenExpiresAt(Instant.now().plusSeconds(token.expiresIn()));
    }
    private String currencyFor(String market) {
        return switch (market) {
            case "200" -> "USD"; case "070" -> "JPY"; case "120" -> "HKD";
            case "160", "170" -> "CNY";
            default -> throw new IllegalArgumentException("지원하는 시장을 선택하세요.");
        };
    }
    private void requireActive(Long member) {
        var owner = members.findById(member).orElseThrow(() -> new IllegalStateException("활성 회원만 연결할 수 있습니다."));
        if (owner.getDisableDate() != null) throw new IllegalStateException("활성 회원만 연결할 수 있습니다.");
    }
    static BigDecimal number(JsonNode row, String key) {
        try {
            String value = text(row, key);
            if (value.isBlank()) throw new NumberFormatException();
            BigDecimal result = new BigDecimal(value);
            if (result.precision() > 30 || Math.abs(result.scale()) > 12) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException e) { throw new IllegalStateException("나무 응답의 금액·수량 형식을 확인할 수 없습니다."); }
    }
    static String text(JsonNode row, String key) { return row.path(key).asText("").trim(); }
}
