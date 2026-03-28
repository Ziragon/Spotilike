package com.spotilike.userservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TokenHashUtilTest {

    @Test
    @DisplayName("Одинаковый вход - одинаковый хеш (детерминированность)")
    void sameInput_sameOutput() {
        String token = "my-refresh-token";
        assertThat(TokenHashUtil.hash(token))
                .isEqualTo(TokenHashUtil.hash(token));
    }

    @Test
    @DisplayName("Разный вход - разный хеш")
    void differentInput_differentOutput() {
        assertThat(TokenHashUtil.hash("token-a"))
                .isNotEqualTo(TokenHashUtil.hash("token-b"));
    }

    @Test
    @DisplayName("Результат - валидный Base64")
    void output_isValidBase64() {
        String hash = TokenHashUtil.hash("test");
        assertThatCode(() ->
                java.util.Base64.getDecoder().decode(hash))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Хеш не совпадает с исходным токеном")
    void hash_isNotPlaintext() {
        String token = "secret-token";
        assertThat(TokenHashUtil.hash(token)).isNotEqualTo(token);
    }
}