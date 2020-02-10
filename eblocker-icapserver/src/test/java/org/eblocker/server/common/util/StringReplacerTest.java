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

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class StringReplacerTest {

    @Test
    public void find() {
        StringReplacer replacer = new StringReplacer()
            .add("first", "1st")
            .add("second", "2nd")
            .add("third", "3rd");
        assertMatch(0, 5, "first", "1st", replacer.find("first second third"));
        assertMatch(6, 12, "second", "2nd", replacer.find(5, "first second third"));
        assertMatch(13, 18, "third", "3rd", replacer.find(12, "first second third"));
        Assert.assertNull(replacer.find(18, "first second third"));
    }

    @Test
    public void findAll() {
        StringReplacer replacer = new StringReplacer()
            .add("first", "1st")
            .add("second", "2nd")
            .add("third", "3rd");

        List<StringReplacer.Match> matches = replacer.findAll("first second third first second third");
        Assert.assertEquals(6, matches.size());
        assertMatch(0, 5, "first", "1st", matches.get(0));
        assertMatch(6, 12, "second", "2nd", matches.get(1));
        assertMatch(13, 18, "third", "3rd", matches.get(2));
        assertMatch(19, 24, "first", "1st", matches.get(3));
        assertMatch(25, 31, "second", "2nd", matches.get(4));
        assertMatch(32, 37, "third", "3rd", matches.get(5));
    }

    @Test
    public void replace() {
        Assert.assertEquals("ABCDEF", new StringReplacer().replace("ABCDEF"));
        Assert.assertEquals("ABABBCCC", new StringReplacer()
            .add("a", "A")
            .add("ba", "BA")
            .add("bb", "BB")
            .add("ccc", "CCC")
            .replace("ababbccc"));
        Assert.assertEquals("xxAxxBAxxBBxxCCCxx", new StringReplacer()
            .add("a", "A")
            .add("ba", "BA")
            .add("bb", "BB")
            .add("ccc", "CCC")
            .replace("xxaxxbaxxbbxxcccxx"));
        Assert.assertEquals("0xdeadbeef", new StringReplacer()
            .add("dead", "beef")
            .add("beef", "dead")
            .add("beefdead", "X")
            .replace("0xbeefdead"));
    }

    private void assertMatch(int startIndex, int endIndex, String target, String replacement, StringReplacer.Match match) {
        Assert.assertEquals(startIndex, match.getStartIndex());
        Assert.assertEquals(endIndex, match.getEndIndex());
        Assert.assertEquals(target, match.getTarget());
        Assert.assertEquals(replacement, match.getReplacement());
    }
}
