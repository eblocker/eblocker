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
package org.eblocker.server.common.util;

import com.google.common.primitives.Chars;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LevenshteinTest {

    private Levenshtein<Character> levenshtein;

    @Before
    public void setup() {
        levenshtein = new Levenshtein.Builder<Character>().build();
    }

    @Test
    public void testSubstitution() {
        List<Character> source = new ArrayList<>(Chars.asList("Carl Carlson".toCharArray()));
        List<Character> target = Chars.asList("Sven Svenson".toCharArray());
        Levenshtein.Distance distance = levenshtein.distance(source, target);
        applyEditSequence(distance.getEditSequence(), source, target);
        Assert.assertEquals(source, target);
    }

    @Test
    public void testInsertionSubstitution() {
        List<Character> source = new ArrayList<>(Chars.asList("kitten".toCharArray()));
        List<Character> target = Chars.asList("sitting".toCharArray());
        Levenshtein.Distance distance = levenshtein.distance(source, target);
        applyEditSequence(distance.getEditSequence(), source, target);
        Assert.assertEquals(source, target);
    }

    @Test
    public void testDeletionSubstitution() {
        List<Character> source = new ArrayList<>(Chars.asList("Saturday".toCharArray()));
        List<Character> target = Chars.asList("Sunday".toCharArray());
        Levenshtein.Distance distance = levenshtein.distance(source, target);
        applyEditSequence(distance.getEditSequence(), source, target);
        Assert.assertEquals(source, target);
    }

    private <T> void applyEditSequence(List<Levenshtein.DistanceMatrixEntry> editSequence, List<T> source, List<T> target) {
        int i = 0;
        for (Levenshtein.DistanceMatrixEntry e : editSequence) {
            switch (e.getOperation()) {
                case NO_OPERATION:
                    ++i;
                    break;
                case INSERT:
                    source.add(i, target.get(e.getY() - 1));
                    ++i;
                    break;
                case DELETE:
                    source.remove(i);
                    break;
                case SUBSTITUTE:
                    source.set(i, target.get(e.getY() - 1));
                    ++i;
                    break;
            }
        }
    }

}
