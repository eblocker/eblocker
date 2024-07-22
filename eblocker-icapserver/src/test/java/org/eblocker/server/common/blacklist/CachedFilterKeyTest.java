/*
 * Copyright 2024 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.blacklist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachedFilterKeyTest {

    @Test
    void equals_same() {
        // Given
        CachedFilterKey sut = new CachedFilterKey(1, 2);

        //When
        //noinspection EqualsWithItself
        boolean equals = sut.equals(sut);

        // Then
        assertTrue(equals);
    }

    @Test
    void equals_sameContent() {
        // Given
        CachedFilterKey sut1 = new CachedFilterKey(1, 2);
        CachedFilterKey sut2 = new CachedFilterKey(1, 2);

        //When
        boolean equals = sut1.equals(sut2);

        //Then
        assertTrue(equals);
    }

    @Test
    void equals_null() {
        // Given
        CachedFilterKey sut = new CachedFilterKey(1, 2);

        //When
        @SuppressWarnings("ConstantValue")
        boolean equals = sut.equals(null);

        //Then
        assertFalse(equals);
    }

    @Test
    void equals_DiffId() {
        // Given
        CachedFilterKey sut1 = new CachedFilterKey(1, 3);
        CachedFilterKey sut2 = new CachedFilterKey(2, 3);

        // When
        boolean equals = sut1.equals(sut2);

        // Then
        assertFalse(equals);
    }

    @Test
    void equals_DiffVersion() {
        // Given
        CachedFilterKey sut1 = new CachedFilterKey(1, 3);
        CachedFilterKey sut2 = new CachedFilterKey(1, 4);

        // When
        boolean equals = sut1.equals(sut2);

        // Then
        assertFalse(equals);
    }

    @Test
    void hashCode_SameContent() {
        // Given
        CachedFilterKey sut1 = new CachedFilterKey(1, 3);
        CachedFilterKey sut2 = new CachedFilterKey(1, 3);

        // When
        int diffHashCode = sut1.hashCode() - sut2.hashCode();
        // Then
        assertEquals(0, diffHashCode);
    }
}