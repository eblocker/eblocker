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
package org.eblocker.server.common.openvpn.configuration;

import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class OpenVpnConfigurationParserTest {

    private final static String TEST_FILE_OPTIONS_GROUP = "classpath:test-data/vpn/openvpn-test.options.group";

    @Test
    public void testParsingRealConfig() throws OpenVpnConfigurationParser.ParseException {
        String config = ResourceHandler.load(new SimpleResource("classpath:test-data/vpn/Amsterdam2-256.ovpn"));

        OpenVpnConfigurationParser parser = new OpenVpnConfigurationParser(TEST_FILE_OPTIONS_GROUP);
        List<Option> options = parser.parse(config);

        Assert.assertEquals(39, options.size());
    }

    @Test
    public void testParsing() throws OpenVpnConfigurationParser.ParseException {
        String config = ResourceHandler.load(new SimpleResource("classpath:test-data/vpn/test.ovpn"));

        OpenVpnConfigurationParser parser = new OpenVpnConfigurationParser(TEST_FILE_OPTIONS_GROUP);
        List<Option> options = parser.parse(config);

        // check option without arguments
        Option option = options.get(0);
        Assert.assertEquals("option-no-args", option.getName());
        Assert.assertEquals(1, option.getLineNumber());
        Assert.assertTrue(option instanceof SimpleOption);
        Assert.assertNull(((SimpleOption)option).getArguments());

        // check option with arguments
        option = options.get(1);
        Assert.assertEquals("option-with-args", option.getName());
        Assert.assertEquals(2, option.getLineNumber());
        Assert.assertTrue(option instanceof SimpleOption);
        Assert.assertNotNull(((SimpleOption)option).getArguments());
        Assert.assertArrayEquals(new String[]{"0", "1", "2", "3"}, ((SimpleOption)option).getArguments());

        // check option with tab separated arguments
        option = options.get(2);
        Assert.assertEquals("option-with-args-tabs", option.getName());
        Assert.assertEquals(4, option.getLineNumber());
        Assert.assertTrue(option instanceof SimpleOption);
        Assert.assertNotNull(((SimpleOption)option).getArguments());
        Assert.assertArrayEquals(new String[]{"0", "1", "2", "3"}, ((SimpleOption)option).getArguments());

        // check inline option
        option = options.get(3);
        Assert.assertEquals("option-inline", option.getName());
        Assert.assertEquals(5, option.getLineNumber());
        Assert.assertTrue(option instanceof InlineOption);
        Assert.assertNotNull(((InlineOption)option).getContent());
        Assert.assertEquals("inline\n  content\n    with\n      indentation!\n", ((InlineOption)option).getContent());

        // check ignored option
        option = options.get(4);
        Assert.assertEquals("option-ignored", option.getName());
        Assert.assertEquals(11, option.getLineNumber());

        // check blacklisted option
        option = options.get(5);
        Assert.assertEquals("option-blacklisted", option.getName());
        Assert.assertEquals(12, option.getLineNumber());

        // check options group
        option = options.get(6);
        Assert.assertEquals("options-group", option.getName());
        Assert.assertEquals(13, option.getLineNumber());
        Assert.assertTrue(option instanceof OptionsGroup);
        OptionsGroup optionsGroup = (OptionsGroup) option;
        Assert.assertNotNull(optionsGroup.getOptions());
        Assert.assertEquals(2, optionsGroup.getOptions().size());
        Assert.assertEquals(14, optionsGroup.getOptions().get(0).getLineNumber());
        Assert.assertTrue(optionsGroup.getOptions().get(0) instanceof SimpleOption);
        Assert.assertArrayEquals(new String[]{"group-0", "none", "first"}, ((SimpleOption)optionsGroup.getOptions().get(0)).getArguments());
        Assert.assertEquals(15, optionsGroup.getOptions().get(1).getLineNumber());
        Assert.assertTrue(optionsGroup.getOptions().get(1) instanceof SimpleOption);
        Assert.assertArrayEquals(new String[]{"group-0", "none", "last"}, ((SimpleOption)optionsGroup.getOptions().get(1)).getArguments());

        // check nested options group
        option = options.get(7);
        Assert.assertEquals("options-group", option.getName());
        Assert.assertEquals(17, option.getLineNumber());
        Assert.assertTrue(option instanceof OptionsGroup);
        optionsGroup = (OptionsGroup) option;
        Assert.assertNotNull(optionsGroup.getOptions());
        Assert.assertEquals(3, optionsGroup.getOptions().size());
        Assert.assertEquals(18, optionsGroup.getOptions().get(0).getLineNumber());
        Assert.assertTrue(optionsGroup.getOptions().get(0) instanceof SimpleOption);
        Assert.assertArrayEquals(new String[]{"group-1", "none", "first"}, ((SimpleOption)optionsGroup.getOptions().get(0)).getArguments());
        Assert.assertEquals(19, optionsGroup.getOptions().get(1).getLineNumber());
        Assert.assertTrue(optionsGroup.getOptions().get(1) instanceof OptionsGroup);
        OptionsGroup nestedOptionsGroup = (OptionsGroup) optionsGroup.getOptions().get(1);
        Assert.assertNotNull(nestedOptionsGroup.getOptions());
        Assert.assertEquals(2, nestedOptionsGroup.getOptions().size());
        Assert.assertEquals(20, nestedOptionsGroup.getOptions().get(0).getLineNumber());
        Assert.assertTrue(nestedOptionsGroup.getOptions().get(0) instanceof SimpleOption);
        Assert.assertArrayEquals(new String[]{"group-2", "group-1", "inner"}, ((SimpleOption)nestedOptionsGroup.getOptions().get(0)).getArguments());
        Assert.assertTrue(nestedOptionsGroup.getOptions().get(1) instanceof InlineOption);
        Assert.assertEquals("      Inline content with\n      unbalanced group tags\n      <options-group>\n      </options-group>\n      </options-group>\n", ((InlineOption)nestedOptionsGroup.getOptions().get(1)).getContent());
        Assert.assertEquals(29, optionsGroup.getOptions().get(2).getLineNumber());
        Assert.assertTrue(optionsGroup.getOptions().get(2) instanceof SimpleOption);
        Assert.assertArrayEquals(new String[]{"group-1", "none", "last"}, ((SimpleOption)optionsGroup.getOptions().get(2)).getArguments());
    }

    @Test(expected = OpenVpnConfigurationParser.ParseException.class)
    public void testParsingUnclosedInlineContent() throws OpenVpnConfigurationParser.ParseException {
        OpenVpnConfigurationParser parser = new OpenVpnConfigurationParser(TEST_FILE_OPTIONS_GROUP);
        parser.parse("<inline-content>\n012345678\n");
    }

    @Test(expected = OpenVpnConfigurationParser.ParseException.class)
    public void testParsingUnclosedOptionsGroup() throws OpenVpnConfigurationParser.ParseException {
        OpenVpnConfigurationParser parser = new OpenVpnConfigurationParser(TEST_FILE_OPTIONS_GROUP);
        parser.parse("<options-group>\noption\n");
    }

    @Test(expected = OpenVpnConfigurationParser.ParseException.class)
    public void testParsingUnexpectedClosingTag() throws OpenVpnConfigurationParser.ParseException {
        OpenVpnConfigurationParser parser = new OpenVpnConfigurationParser(TEST_FILE_OPTIONS_GROUP);
        parser.parse("option\n</options-group>\n");
    }

    @Test(expected = OpenVpnConfigurationParser.ParseException.class)
    public void testParsingWrongClosingTag() throws OpenVpnConfigurationParser.ParseException {
        OpenVpnConfigurationParser parser = new OpenVpnConfigurationParser(TEST_FILE_OPTIONS_GROUP);
        parser.parse("<options-group>\noption\n</foobar>\n");
    }

    // EB1-2026
    @Test
    public void testParsingArguments() throws OpenVpnConfigurationParser.ParseException {
        assertOption(parseSingleOption("option"), "option");
        assertOption(parseSingleOption("option 0 1 2"), "option", "0", "1", "2");
        assertOption(parseSingleOption("option  0 1 2"), "option", "0", "1", "2");
        assertOption(parseSingleOption("option\t0\t\t1 2"), "option", "0", "1", "2");
    }

    @SuppressWarnings("unchecked")
    private <T extends Option> T parseSingleOption(String config) throws OpenVpnConfigurationParser.ParseException {
        List<Option> options = new OpenVpnConfigurationParser(TEST_FILE_OPTIONS_GROUP).parse(config);
        Assert.assertNotNull(options);
        Assert.assertEquals(1, options.size());
        return (T) options.get(0);
    }

    private void assertOption(SimpleOption option, String name, String... arguments) {
        Assert.assertEquals(name, option.getName());
        if (arguments.length != 0) {
            Assert.assertNotNull(option.getArguments());
            Assert.assertEquals(arguments.length, option.getArguments().length);
            for (int i = 0; i < arguments.length; ++i) {
                Assert.assertEquals(arguments[i], option.getArguments()[i]);
            }
        } else {
            Assert.assertNull(option.getArguments());
        }
    }

}
