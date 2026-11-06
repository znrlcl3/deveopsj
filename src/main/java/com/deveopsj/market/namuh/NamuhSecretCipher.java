package com.deveopsj.market.namuh;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NamuhSecretCipher {
    private final byte[] key;
    public NamuhSecretCipher(@Value("${app.namuh.encryption-key:${NAMUH_ENCRYPTION_KEY:}}") String configured) {
        byte[] parsed;
        try { parsed = Base64.getDecoder().decode(configured); } catch (IllegalArgumentException e) { parsed = new byte[0]; }
        key = parsed.length == 32 ? parsed : null;
    }
    public boolean ready() { return key != null; }
    public String encrypt(Long memberId, String purpose, String value) {
        requireReady();
        try {
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD((memberId + ":" + purpose).getBytes(StandardCharsets.UTF_8));
            byte[] result = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(result);
        } catch (Exception e) { throw new IllegalStateException("나무 연결정보 암호화에 실패했습니다."); }
    }
    public String decrypt(Long memberId, String purpose, String value) {
        requireReady();
        try {
            String[] parts = value.split("\\.", 2);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            cipher.updateAAD((memberId + ":" + purpose).getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("연결정보를 읽을 수 없습니다. 서버 암호화 설정을 확인해 주세요."); }
    }
    private void requireReady() {
        if (!ready()) throw new IllegalStateException("서버의 나무 연결 암호화 설정이 필요합니다. 관리자에게 문의해 주세요.");
    }
}
