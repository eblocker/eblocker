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

import com.google.common.collect.Sets;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OpenVpnConfiguratorTest {
    private final static String OPTIONS_BLACKLIST_FILE = "classpath:openvpn.options.blacklist";
    private final static String OPTIONS_WHITELIST_FILE = "classpath:openvpn.options.whitelist";

    private final static String TEST_OPTIONS_BLACKLIST_FILE = "classpath:test-data/vpn/openvpn-test.options.blacklist";
    private final static String TEST_OPTIONS_WHITELIST_FILE = "classpath:test-data/vpn/openvpn-test.options.whitelist";
    private final static String TEST_OPTIONS_INLINE_FILE = "classpath:test-data/vpn/openvpn-test.options.inline";
    private final static String TEST_OPTIONS_DEFAULT_FILE = "classpath:test-data/vpn/openvpn-test.default-options.ovpn";

    private List<Option> testUserOptions;
    private List<Option> testEblockerOptions;

    private OpenVpnConfigurator configurator;
    private OpenVpnConfigurationParser parser;

    @Before
    public void setup() throws OpenVpnConfigurationParser.ParseException {
        // setup parser / config mock
        parser = Mockito.mock(OpenVpnConfigurationParser.class);
        testEblockerOptions = Arrays.asList(
                new SimpleOption(1, "option-with-args", new String[]{ "4" }),
                new SimpleOption(2, "eblocker-option")
        );
        Mockito.when(parser.parse("#default-options")).thenReturn(testEblockerOptions);

        testUserOptions = Arrays.asList(
                new SimpleOption(1, "option-no-args"),
                new SimpleOption(2, "option-with-args", new String[]{ "0", "1", "2", "3" }),
                new InlineOption(3, "option-inline", "content"),
                new SimpleOption(4, "option-requiring-inlining", new String[]{ "fileName", "additionalArgument" }),
                new SimpleOption(5, "option-blacklisted"),
                new SimpleOption(6, "option-ignored"));
        Mockito.when(parser.parse("test-config")).thenReturn(testUserOptions);

        configurator = new OpenVpnConfigurator(TEST_OPTIONS_BLACKLIST_FILE, TEST_OPTIONS_WHITELIST_FILE, TEST_OPTIONS_INLINE_FILE, TEST_OPTIONS_DEFAULT_FILE, () -> parser);
    }

    @Test
    public void testOptionsList() {
        Set<String> blacklist = getOptions(OPTIONS_BLACKLIST_FILE);
        Set<String> whitelist = getOptions(OPTIONS_WHITELIST_FILE);
        Assert.assertTrue("black- and whitelist must be disjunct!", Sets.intersection(blacklist, whitelist).isEmpty());
    }

    @Test
    public void testConfigCreation() throws OpenVpnConfigurationParser.ParseException {
        // create configuration
        OpenVpnConfiguration configuration = configurator.createConfiguration("test-config");

        // check create configuration
        Assert.assertEquals("test-config", configuration.getSourceConfig());
        Assert.assertEquals(testUserOptions, configuration.getUserOptions());
    }

    @Test
    public void testActiveConfiguration() {
        // setup mock config
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(testUserOptions);
        Map<String, String> inlinedContentByName = Collections.singletonMap("option-requiring-inlining", "content");
        configuration.setInlinedContentByName(inlinedContentByName);

        // create active configuration
        List<Option> options = configurator.getActiveConfiguration(configuration, "credentials.txt", Collections.singletonMap("option-requiring-inlining", "option.option-requiring-inlining"));

        // check options are correct
        Assert.assertNotNull(options);
        Assert.assertEquals(5, options.size());
        Assert.assertSame(testUserOptions.get(0), options.get(0));
        Assert.assertSame(testEblockerOptions.get(0), options.get(1));
        Assert.assertSame(testUserOptions.get(2), options.get(2));
        assertSimpleOption("option-requiring-inlining", new String[]{ "option.option-requiring-inlining", "additionalArgument" }, options.get(3));
        Assert.assertSame(testEblockerOptions.get(1), options.get(4));
    }

    @Test
    public void testActiveConfigurationStaticToDynamicTun() {
        // setup mock config
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(Arrays.asList(
                new SimpleOption(1, "client"),
                new SimpleOption(2, "dev", new String[]{ "tun23" }),
                new SimpleOption(3, "remote", new String[]{ "remote" })));

        // create active configuration
        List<Option> options = configurator.getActiveConfiguration(configuration, "credentials.txt", Collections.emptyMap());

        // check dev option has been overridden
        Assert.assertNotNull(options);
        SimpleOption devOption = (SimpleOption) options.stream().filter(o -> "dev".equals(o.getName())).findAny().orElse(null);
        Assert.assertNotNull(devOption);
        Assert.assertNotNull(devOption.getArguments());
        Assert.assertEquals(1, devOption.getArguments().length);
        Assert.assertEquals("tun", devOption.getArguments()[0]);
    }

    @Test
    public void testOptionsByState() {
        // setup configuration mock
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(testUserOptions);

        // retrieve user options by state
        Map<OpenVpnConfigurator.OptionState, Set<Option>> userOptionByState = configurator.getUserOptionsByState(configuration.getUserOptions());

        // check user options are categorized correctly
        Assert.assertNotNull(userOptionByState);

        // check active options
        Assert.assertNotNull(userOptionByState.get(OpenVpnConfigurator.OptionState.ACTIVE));
        Assert.assertEquals(new HashSet<>(testUserOptions.subList(0, 3)), userOptionByState.get(OpenVpnConfigurator.OptionState.ACTIVE));

        // check blacklisted option
        Assert.assertNotNull(userOptionByState.get(OpenVpnConfigurator.OptionState.BLACKLISTED));
        Assert.assertEquals(Collections.singleton(testUserOptions.get(4)), userOptionByState.get(OpenVpnConfigurator.OptionState.BLACKLISTED));

        // check ignored option
        Assert.assertNotNull(userOptionByState.get(OpenVpnConfigurator.OptionState.IGNORED));
        Assert.assertEquals(Collections.singleton(testUserOptions.get(5)), userOptionByState.get(OpenVpnConfigurator.OptionState.IGNORED));

        // check inlining-required option
        Assert.assertNotNull(userOptionByState.get(OpenVpnConfigurator.OptionState.FILE_REQUIRED));
        Assert.assertEquals(Collections.singleton(testUserOptions.get(3)), userOptionByState.get(OpenVpnConfigurator.OptionState.FILE_REQUIRED));
    }

    @Test
    public void testDefaultOptions() {
        Assert.assertEquals(testEblockerOptions.size(), configurator.getEblockerOptions().size());
        Assert.assertTrue(testEblockerOptions.containsAll(configurator.getEblockerOptions()));
    }

    @Test
    public void testCannotOverrideDefaultOptions() {
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(Arrays.asList(
                new SimpleOption(1, "option-with-args"),
                new SimpleOption(1, "option-with-args", new String[]{ "1" }),
                new SimpleOption(1, "eblocker-option", new String[]{ "1" })
        ));

        List<Option> activeOptions = configurator.getActiveConfiguration(configuration, null, Collections.emptyMap());
        Assert.assertEquals(2, activeOptions.size());
        assertSimpleOption("option-with-args", new String[]{ "4" }, activeOptions.get(0));
        assertSimpleOption("eblocker-option", null, activeOptions.get(1));
    }

    @Test
    public void testCredentialsRequired() {
        // setup mock
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(Arrays.asList(new SimpleOption(1, "auth-user-pass", new String[0])));

        // test
        Assert.assertTrue(configurator.credentialsRequired(configuration));
    }

    @Test
    public void testNoCredentialsRequiredWithExternalCredentials() {
        // setup mock
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(Arrays.asList(new SimpleOption(1, "auth-user-pass", new String[]{ "secret.txt" })));

        // test
        Assert.assertFalse(configurator.credentialsRequired(configuration));
    }

    @Test
    public void testNoCredentialsRequired() {
        // setup mock
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(new ArrayList<>());

        // test
        Assert.assertFalse(configurator.credentialsRequired(configuration));
    }

    @Test
    public void testOverridingCredentialsOption() {
        // setup config
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(Arrays.asList(new SimpleOption(1, "auth-user-pass")));

        // retrieve active configuration
        List<Option> options = configurator.getActiveConfiguration(configuration, "credentials.txt", Collections.emptyMap());

        // check option has been overridden
        Assert.assertEquals(3, options.size()); // 2 default + 1 auth-user-pass
        assertSimpleOption("auth-user-pass", new String[]{ "credentials.txt" }, options.get(2));
    }

    @Test
    public void testOverridingExternalCredentialsOption() {
        // setup config
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(Arrays.asList(new SimpleOption(1, "auth-user-pass", new String[]{ "random-dude-password.txt" })));

        // retrieve active configuration
        List<Option> options = configurator.getActiveConfiguration(configuration, "credentials.txt", Collections.emptyMap());

        // check option has been overridden
        Assert.assertEquals(3, options.size()); // 2 default + 1 auth-user-pass
        assertSimpleOption("auth-user-pass", new String[]{ "credentials.txt" }, options.get(2));
    }

    @Test
    public void testValidationEmptyConfig() {
        // create empty config
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(Collections.emptyList());

        // validate config
        List<String> errorKeys = configurator.validateConfiguration(configuration);

        // check validation found all errors
        Assert.assertNotNull(errorKeys);
        Assert.assertEquals(3, errorKeys.size());
        Assert.assertTrue(errorKeys.contains(OpenVpnConfigurator.VPN_CONFIG_ERROR_OPTION_CLIENT_MISSING));
        Assert.assertTrue(errorKeys.contains(OpenVpnConfigurator.VPN_CONFIG_ERROR_OPTION_DEV_MISSING));
        Assert.assertTrue(errorKeys.contains(OpenVpnConfigurator.VPN_CONFIG_ERROR_OPTION_REMOTE_MISSING));
    }

    @Test
    public void testValidationWrongDevType() {
        // create empty config
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(Arrays.asList(
                new SimpleOption(1, "client"),
                new SimpleOption(2, "dev", new String[]{ "tap" }),
                new SimpleOption(3, "remote", new String[]{ "remote" })));

        // validate config
        List<String> errorKeys = configurator.validateConfiguration(configuration);

        // check validation catched unsupported dev option
        Assert.assertNotNull(errorKeys);
        Assert.assertEquals(1, errorKeys.size());
        Assert.assertTrue(errorKeys.contains(OpenVpnConfigurator.VPN_CONFIG_ERROR_OPTION_DEV_TUN_SUPPORTED));
    }

    @Test
    public void testValidationStaticTunDev() {
        // create empty config
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(Arrays.asList(
                new SimpleOption(1, "client"),
                new SimpleOption(2, "dev", new String[]{ "tun123" }),
                new SimpleOption(3, "remote", new String[]{ "remote" })));

        // validate config
        List<String> errorKeys = configurator.validateConfiguration(configuration);

        // check validation catched unsupported dev option
        Assert.assertNotNull(errorKeys);
        Assert.assertEquals(0, errorKeys.size());
    }

    @Test
    public void testValidationMinimal() {
        // create empty config
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(Arrays.asList(
                new SimpleOption(1, "client"),
                new SimpleOption(2, "dev", new String[]{ "tun" }),
                new SimpleOption(3, "remote", new String[]{ "remote" })));

        // validate config
        List<String> errorKeys = configurator.validateConfiguration(configuration);

        // check validation no errors have been found
        Assert.assertNotNull(errorKeys);
        Assert.assertEquals(0, errorKeys.size());
    }

    @Test
    public void testValidationNoRemote() {
        // create empty config
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(Arrays.asList(
                new SimpleOption(1, "client"),
                new SimpleOption(2, "dev", new String[]{ "tun" })));

        // validate config
        List<String> errorKeys = configurator.validateConfiguration(configuration);

        // check validation errors have been found
        Assert.assertNotNull(errorKeys);
        Assert.assertEquals(1, errorKeys.size());
        Assert.assertEquals(OpenVpnConfigurator.VPN_CONFIG_ERROR_OPTION_REMOTE_MISSING, errorKeys.get(0));
    }

    @Test
    public void testValidationConnectionRemote() {
        // create empty config
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(Arrays.asList(
                new SimpleOption(1, "client"),
                new SimpleOption(2, "dev", new String[]{ "tun" }),
                new OptionsGroup(3, "connection", Collections.singletonList(new SimpleOption(4, "remote", new String[]{ "remote-0" }))),
                new OptionsGroup(6, "connection", Collections.singletonList(new SimpleOption(7, "remote", new String[]{ "remote-1" }))),
                new SimpleOption(9, "remote", new String[]{ "remote" }))
        );

        // validate config
        List<String> errorKeys = configurator.validateConfiguration(configuration);

        // check validation no errors have been found
        Assert.assertNotNull(errorKeys);
        Assert.assertEquals(0, errorKeys.size());
    }

    @Test
    public void testValidationConnectionRemoteAnDefaultRemote() {
        // create empty config
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(Arrays.asList(
                new SimpleOption(1, "client"),
                new SimpleOption(2, "dev", new String[]{ "tun" }),
                new OptionsGroup(3, "connection", Collections.singletonList(new SimpleOption(4, "remote", new String[]{ "remote-0" }))),
                new OptionsGroup(6, "connection", Collections.singletonList(new SimpleOption(7, "remote", new String[]{ "remote-1" }))))
        );

        // validate config
        List<String> errorKeys = configurator.validateConfiguration(configuration);

        // check validation no errors have been found
        Assert.assertNotNull(errorKeys);
        Assert.assertEquals(0, errorKeys.size());
    }

    @Test
    public void testValidationConnectionRemoteMissing() {
        // create empty config
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(Arrays.asList(
                new SimpleOption(1, "client"),
                new SimpleOption(2, "dev", new String[]{ "tun" }),
                new OptionsGroup(3, "connection", Collections.singletonList(new SimpleOption(4, "remote", new String[]{ "remote-0" }))),
                new OptionsGroup(6, "connection", Collections.singletonList(new SimpleOption(7, "some-option")))
        ));

        // validate config
        List<String> errorKeys = configurator.validateConfiguration(configuration);

        // check validation errors have been found
        Assert.assertNotNull(errorKeys);
        Assert.assertEquals(1, errorKeys.size());
        Assert.assertEquals(OpenVpnConfigurator.VPN_CONFIG_ERROR_OPTION_REMOTE_MISSING, errorKeys.get(0));
    }

    @Test
    public void testValidateInlineOption() {
        // create empty config
        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(Arrays.asList(
                new SimpleOption(1, "client"),
                new SimpleOption(2, "dev", new String[]{ "tun" }),
                new SimpleOption(3, "remote", new String[]{ "remote" }),
                new SimpleOption(1, "option-inline")));

        // validate config
        List<String> errorKeys = configurator.validateConfiguration(configuration);

        // check validation error
        Assert.assertNotNull(errorKeys);
        Assert.assertEquals(1, errorKeys.size());
        Assert.assertTrue(errorKeys.contains(OpenVpnConfigurator.VPN_CONFIG_ERROR_OPTION_MISSING_FILE_REFERENCE));
    }

    @Test
    public void testValidationClientPullTlsClientValidation() {
        Assert.assertTrue(configurator.validateConfiguration(createConfig(false, false, false)).contains(OpenVpnConfigurator.VPN_CONFIG_ERROR_OPTION_CLIENT_MISSING));
        Assert.assertTrue(configurator.validateConfiguration(createConfig(false, false, true)).contains(OpenVpnConfigurator.VPN_CONFIG_ERROR_OPTION_CLIENT_MISSING));
        Assert.assertTrue(configurator.validateConfiguration(createConfig(false, true, false)).contains(OpenVpnConfigurator.VPN_CONFIG_ERROR_OPTION_CLIENT_MISSING));
        Assert.assertFalse(configurator.validateConfiguration(createConfig(false, true, true)).contains(OpenVpnConfigurator.VPN_CONFIG_ERROR_OPTION_CLIENT_MISSING));
        Assert.assertFalse(configurator.validateConfiguration(createConfig(true, false, false)).contains(OpenVpnConfigurator.VPN_CONFIG_ERROR_OPTION_CLIENT_MISSING));
        Assert.assertFalse(configurator.validateConfiguration(createConfig(true, false, true)).contains(OpenVpnConfigurator.VPN_CONFIG_ERROR_OPTION_CLIENT_MISSING));
        Assert.assertFalse(configurator.validateConfiguration(createConfig(true, true, false)).contains(OpenVpnConfigurator.VPN_CONFIG_ERROR_OPTION_CLIENT_MISSING));
        Assert.assertFalse(configurator.validateConfiguration(createConfig(true, true, true)).contains(OpenVpnConfigurator.VPN_CONFIG_ERROR_OPTION_CLIENT_MISSING));
    }

    private OpenVpnConfiguration createConfig(boolean client, boolean pull, boolean tlsClient) {
        int i = 1;
        List<Option> options = new ArrayList<>();
        if (client) {
            options.add(new SimpleOption(i++, "client"));
        }
        if (pull) {
            options.add(new SimpleOption(i++, "pull"));
        }
        if (tlsClient) {
            options.add(new SimpleOption(i++, "tls-client"));
        }

        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setUserOptions(options);
        return configuration;
    }

    private Set<String> getOptions(String fileName) {
        return ResourceHandler.readLinesAsSet(new SimpleResource(fileName));
    }

    private void assertSimpleOption(String expectedName, String[] expectedArguments, Option option) {
        Assert.assertTrue(option instanceof SimpleOption);
        Assert.assertEquals(expectedName, option.getName());
        Assert.assertArrayEquals(expectedArguments, ((SimpleOption) option).getArguments());
    }
}
