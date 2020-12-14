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
package org.eblocker.server.icap.filter.url;

import org.junit.Assert;
import org.junit.Test;

public class ContentMatcherTest {

    @Test
    public void testAcceptType() {
        // setup
        ContentMatcher matcher = new ContentMatcher(new String[]{ "type/subtype" }, new String[0]);

        // test
        Assert.assertFalse(matcher.matches("*/*", null));
        Assert.assertFalse(matcher.matches("type/*", null));
        Assert.assertTrue(matcher.matches("type/subtype", null));
        Assert.assertFalse(matcher.matches("type/anothersubtype", null));

        // must never match
        Assert.assertFalse(matcher.matches(null, null));
        Assert.assertFalse(matcher.matches("", null));
    }

    @Test
    public void testAcceptAllTypes() {
        // setup
        ContentMatcher matcher = new ContentMatcher(new String[]{ "*/*" }, new String[0]);

        // test
        Assert.assertTrue(matcher.matches("*/*", null));
        Assert.assertTrue(matcher.matches("type/*", null));
        Assert.assertTrue(matcher.matches("type/subtype", null));

        // must never match
        Assert.assertFalse(matcher.matches(null, null));
        Assert.assertFalse(matcher.matches("", null));
    }

    @Test
    public void testAcceptAllSubTypes() {
        // setup
        ContentMatcher matcher = new ContentMatcher(new String[]{ "type/*" }, new String[0]);

        // test
        Assert.assertFalse(matcher.matches("*/*", null));
        Assert.assertTrue(matcher.matches("type/*", null));
        Assert.assertTrue(matcher.matches("type/subtype", null));
        Assert.assertTrue(matcher.matches("type/anothersubtype", null));

        // must never match
        Assert.assertFalse(matcher.matches(null, null));
        Assert.assertFalse(matcher.matches("", null));
    }

    @Test
    public void testAcceptNothing() {
        // setup
        ContentMatcher matcher = new ContentMatcher(new String[0], new String[0]);

        // test
        Assert.assertFalse(matcher.matches("*/*", null));
        Assert.assertFalse(matcher.matches("type/*", null));
        Assert.assertFalse(matcher.matches("type/subtype", null));
        Assert.assertFalse(matcher.matches("type/anothersubtype", null));

        // must never match
        Assert.assertFalse(matcher.matches(null, null));
        Assert.assertFalse(matcher.matches("", null));
    }

    @Test
    public void testMultipleAccept() {
        // setup
        ContentMatcher matcher = new ContentMatcher(new String[]{ "type/subtype", "type/anothersubtype", "fancy/*" }, new String[0]);

        // test
        Assert.assertFalse(matcher.matches("*/*", null));
        Assert.assertFalse(matcher.matches("type/*", null));
        Assert.assertTrue(matcher.matches("type/subtype", null));
        Assert.assertTrue(matcher.matches("type/anothersubtype", null));
        Assert.assertFalse(matcher.matches("type/unkown", null));
        Assert.assertTrue(matcher.matches("fancy/*", null));
        Assert.assertTrue(matcher.matches("fancy/pants", null));

        // must never match
        Assert.assertFalse(matcher.matches(null, null));
        Assert.assertFalse(matcher.matches("", null));
    }

    @Test
    public void testSuffix() {
        // setup
        ContentMatcher matcher = new ContentMatcher(new String[0], new String[]{ "js" });

        // test
        Assert.assertTrue(matcher.matches(null, "http://fancy.new.site/random.js"));
        Assert.assertTrue(matcher.matches(null, "http://fancy.new.site/random.js?key=value"));
        Assert.assertFalse(matcher.matches(null, "http://fancy.new.site/random.css"));
        Assert.assertFalse(matcher.matches(null, "http://fancy.new.site/random.css?key=value"));
    }

    @Test
    public void testDisjunction() {
        // setup
        ContentMatcher matcher = new ContentMatcher(new String[]{ "application/javascript" }, new String[]{ "js" });

        // test
        Assert.assertFalse(matcher.matches(null, null));
        Assert.assertTrue(matcher.matches(null, "http://fancy.new.site/random.js"));
        Assert.assertTrue(matcher.matches("application/javascript", null));
        Assert.assertTrue(matcher.matches("application/javascript", "http://fancy.new.site/random.js"));
    }
}
