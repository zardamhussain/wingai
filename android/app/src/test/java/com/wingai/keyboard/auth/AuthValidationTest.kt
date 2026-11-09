package com.wingai.keyboard.auth

import org.junit.Assert.*
import org.junit.Test

class AuthValidationTest {
    @Test fun validEmailTrimsOuterSpaces() { assertNull(AuthValidation.email("  me+wing@example.com  ")) }
    @Test fun malformedEmailRejected() {
        listOf("", "a", "a@", "a@b", "a@@example.com", "a b@example.com").forEach { assertNotNull(AuthValidation.email(it)) }
    }
    @Test fun emailLengthBounded() { assertNotNull(AuthValidation.email("a".repeat(250) + "@example.com")) }
    @Test fun newPasswordMinimum() { assertNotNull(AuthValidation.newPassword("1234567")); assertNull(AuthValidation.newPassword("12345678")) }
    @Test fun newPasswordMaximum() { assertNull(AuthValidation.newPassword("x".repeat(128))); assertNotNull(AuthValidation.newPassword("x".repeat(129))) }
    @Test fun passphrasesAndUnicodeNotStripped() { assertNull(AuthValidation.newPassword("  a long passphrase  ")); assertNull(AuthValidation.newPassword("hello-world-123")) }
    @Test fun nameRequired() { assertNotNull(AuthValidation.name("   ")); assertNull(AuthValidation.name(" Ada ")) }
    @Test fun nameLengthBounded() { assertNull(AuthValidation.name("x".repeat(60))); assertNotNull(AuthValidation.name("x".repeat(61))) }
}
