/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License. You may obtain a copy of the License at:
 *
 *   https://joinup.ec.europa.eu/page/eupl-text-11-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.eblocker.server.http.utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class NormalizationUtilsTest {

    public NormalizationUtilsTest() {
        // TODO Auto-generated constructor stub
    }

    @Before
    public void setup() {

    }

    @After
    public void teardown() {

    }

    @Test
    public void test_normalizeStringForShellScript() {
        // No changes expected here
        String allClean = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_";
        String cleaned = NormalizationUtils.normalizeStringForShellScript(allClean);
        assertEquals(allClean, cleaned);

        // Common characters in german, changes expected
        String umlaute = "äöüÄÖÜß";
        String umlauteCleaned = NormalizationUtils.normalizeStringForShellScript(umlaute);
        String umlauteCleanedExpected = "aouAOU";
        assertEquals(umlauteCleanedExpected, umlauteCleaned);

        // All ASCII codes, contain several characters suspicious for command injection (dash, semicolon, etc.)
        for (int i = 0; i < 256; i++) {
            String dirtyChar = Character.toString((char) i);
            String cleanChar = NormalizationUtils.normalizeStringForShellScript(dirtyChar);
//            System.out.println(i + ": " + Character.toString((char) i) + " normalized: " + cleanChar);
            if (i == 9 // Character Tabulation,
                    || i == 10 // Line Feed,
                    || i == 11 // Vertical Tab,
                    || i == 12 // Form Feed,
                    || i == 13 // Carriage Return,
                    || i == 32 // and Space
            ) { // are turned into underscores
                assertEquals("_", cleanChar);
            } else if ((i >= 48 && i <= 57) // Numbers,
                    || (i >= 65 && i <= 90) // Uppercase,
                    || i == 95 // Underscore,
                    || (i >= 97 && i <= 122) // and Lowercase
                    ) {
                // are not changed
                assertEquals(dirtyChar, cleanChar);
            } else if (i >= 192 && i <= 197) {
                // Characters with accents and similar - related to "A"
                assertEquals("A", cleanChar);
            } else if (i == 199) {
                // Characters with accents and similar - related to "C"
                assertEquals("C", cleanChar);
            } else if (i >= 200 && i <= 203) {
                // Characters with accents and similar - related to "E"
                assertEquals("E", cleanChar);
            } else if (i >= 204 && i <= 207) {
                // Characters with accents and similar - related to "I"
                assertEquals("I", cleanChar);
            } else if (i == 209) {
                // Characters with accents and similar - related to "N"
                assertEquals("N", cleanChar);
            } else if (i >= 210 && i <= 214) {
                // Characters with accents and similar - related to "O"
                assertEquals("O", cleanChar);
            } else if (i >= 217 && i <= 220) {
                // Characters with accents and similar - related to "U"
                assertEquals("U", cleanChar);
            } else if (i == 221) {
                // Characters with accents and similar - related to "Y"
                assertEquals("Y", cleanChar);
            } else if (i >= 224 && i <= 229) {
                // Characters with accents and similar - related to "a"
                assertEquals("a", cleanChar);
            } else if (i == 231) {
                // Characters with accents and similar - related to "c"
                assertEquals("c", cleanChar);
            } else if (i >= 232 && i <= 235) {
                // Characters with accents and similar - related to "e"
                assertEquals("e", cleanChar);
            } else if (i >= 236 && i <= 239) {
                // Characters with accents and similar - related to "i"
                assertEquals("i", cleanChar);
            } else if (i == 241) {
                // Characters with accents and similar - related to "n"
                assertEquals("n", cleanChar);
            } else if (i >= 242 && i <= 246) {
                // Characters with accents and similar - related to "o"
                assertEquals("o", cleanChar);
            } else if (i >= 249 && i <= 252) {
                // Characters with accents and similar - related to "u"
                assertEquals("u", cleanChar);
            } else if (i == 253 || i == 255) {
                // Characters with accents and similar - related to "y"
                assertEquals("y", cleanChar);
            } else {
                assertEquals("", cleanChar);
            }
        }

        // All unicode characters - they are only changed to safe characters
        for(int i = 0x00;i<0x10FFFF;i++){
            String unicodeChar = Character.toString((char)i);
            String cleanedUnicodeChar = NormalizationUtils.normalizeStringForShellScript(unicodeChar);
            assertTrue(allClean.contains(cleanedUnicodeChar));
        }
    }

    @Test
    public void test_normalizeStringForFileName() {
        // No changes expected here
        String allClean = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_";
        String cleaned = NormalizationUtils.normalizeStringForFilename(allClean, 63, "");
        assertEquals(allClean, cleaned);

        // Common characters in german, changes expected
        String umlaute = "äöüÄÖÜß";
        String umlauteCleaned = NormalizationUtils.normalizeStringForFilename(umlaute, 7, "");
        String umlauteCleanedExpected = "aouAOU";
        assertEquals(umlauteCleanedExpected, umlauteCleaned);

        // All ASCII codes, contain several characters suspicious for command injection (dash, semicolon, etc.)
        for (int i = 0; i < 256; i++) {
            String dirtyChar = Character.toString((char) i);
            String cleanChar = NormalizationUtils.normalizeStringForFilename(dirtyChar, 1, "");
//            System.out.println(i + ": " + Character.toString((char) i) + " normalized: " + cleanChar);
            if (i == 9 // Character Tabulation,
                    || i == 10 // Line Feed,
                    || i == 11 // Vertical Tab,
                    || i == 12 // Form Feed,
                    || i == 13 // Carriage Return,
                    || i == 32 // and Space
            ) { // are turned into underscores
                assertEquals("_", cleanChar);
            } else if ((i >= 48 && i <= 57) // Numbers,
                    || (i >= 65 && i <= 90) // Uppercase,
                    || i == 95 // Underscore,
                    || (i >= 97 && i <= 122) // and Lowercase
                    ) {
                // are not changed
                assertEquals(dirtyChar, cleanChar);
            } else if (i >= 192 && i <= 197) {
                // Characters with accents and similar - related to "A"
                assertEquals("A", cleanChar);
            } else if (i == 199) {
                // Characters with accents and similar - related to "C"
                assertEquals("C", cleanChar);
            } else if (i >= 200 && i <= 203) {
                // Characters with accents and similar - related to "E"
                assertEquals("E", cleanChar);
            } else if (i >= 204 && i <= 207) {
                // Characters with accents and similar - related to "I"
                assertEquals("I", cleanChar);
            } else if (i == 209) {
                // Characters with accents and similar - related to "N"
                assertEquals("N", cleanChar);
            } else if (i >= 210 && i <= 214) {
                // Characters with accents and similar - related to "O"
                assertEquals("O", cleanChar);
            } else if (i >= 217 && i <= 220) {
                // Characters with accents and similar - related to "U"
                assertEquals("U", cleanChar);
            } else if (i == 221) {
                // Characters with accents and similar - related to "Y"
                assertEquals("Y", cleanChar);
            } else if (i >= 224 && i <= 229) {
                // Characters with accents and similar - related to "a"
                assertEquals("a", cleanChar);
            } else if (i == 231) {
                // Characters with accents and similar - related to "c"
                assertEquals("c", cleanChar);
            } else if (i >= 232 && i <= 235) {
                // Characters with accents and similar - related to "e"
                assertEquals("e", cleanChar);
            } else if (i >= 236 && i <= 239) {
                // Characters with accents and similar - related to "i"
                assertEquals("i", cleanChar);
            } else if (i == 241) {
                // Characters with accents and similar - related to "n"
                assertEquals("n", cleanChar);
            } else if (i >= 242 && i <= 246) {
                // Characters with accents and similar - related to "o"
                assertEquals("o", cleanChar);
            } else if (i >= 249 && i <= 252) {
                // Characters with accents and similar - related to "u"
                assertEquals("u", cleanChar);
            } else if (i == 253 || i == 255) {
                // Characters with accents and similar - related to "y"
                assertEquals("y", cleanChar);
            } else {
                assertEquals("", cleanChar);
            }
        }

        // All unicode characters - they are only changed to safe characters
        for(int i = 0x00;i<0x10FFFF;i++){
            String unicodeChar = Character.toString((char)i);
            String cleanedUnicodeChar = NormalizationUtils.normalizeStringForShellScript(unicodeChar);
            assertTrue(allClean.contains(cleanedUnicodeChar));
        }

        // Strings are shortened
        assertEquals("Vollstandige",
                NormalizationUtils.normalizeStringForFilename("Vollständiger Satz wird schön gekürzt", 12, ""));

        // Fallback String is used
        assertEquals("fallback", NormalizationUtils.normalizeStringForFilename(null, 10, "fallback"));
        assertEquals("fallback", NormalizationUtils.normalizeStringForFilename("",   10, "fallback"));
        assertEquals("fallback", NormalizationUtils.normalizeStringForFilename("ß′¹²³¼½¬{[]}", 10, "fallback"));
    }
}
