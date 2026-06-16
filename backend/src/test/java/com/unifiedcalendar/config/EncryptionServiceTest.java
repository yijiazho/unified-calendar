package com.unifiedcalendar.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncryptionService")
class EncryptionServiceTest {

    private EncryptionService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new EncryptionService("test-encryption-key-32chars-min!");
    }

    @Test
    @DisplayName("decrypt(encrypt(plaintext)) round-trips correctly")
    void encryptDecryptRoundTrip() {
        String plaintext = "my-secret-token-value";
        assertEquals(plaintext, service.decrypt(service.encrypt(plaintext)));
    }

    @Test
    @DisplayName("empty string round-trips correctly")
    void encryptDecryptEmptyString() {
        assertEquals("", service.decrypt(service.encrypt("")));
    }

    @Test
    @DisplayName("encrypting the same value twice produces different ciphertexts (random nonce)")
    void sameInputProducesDistinctCiphertexts() {
        String plaintext = "access-token";
        assertNotEquals(service.encrypt(plaintext), service.encrypt(plaintext));
    }

    @Test
    @DisplayName("distinct plaintexts produce distinct ciphertexts")
    void distinctPlaintextsProduceDifferentCiphertexts() {
        assertNotEquals(service.encrypt("token-a"), service.encrypt("token-b"));
    }

    @Test
    @DisplayName("tampered ciphertext throws on decrypt")
    void tamperedCiphertextThrows() {
        String encrypted = service.encrypt("sensitive");
        String tampered  = encrypted.substring(0, encrypted.length() - 4) + "XXXX";
        assertThrows(RuntimeException.class, () -> service.decrypt(tampered));
    }
}
