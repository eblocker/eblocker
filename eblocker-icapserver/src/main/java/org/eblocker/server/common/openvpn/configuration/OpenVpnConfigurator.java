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
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OpenVpnConfigurator {

    static final String VPN_CONFIG_ERROR_OPTION_CLIENT_MISSING = "VPN_CONFIG_ERROR_OPTION_CLIENT_MISSING";
    static final String VPN_CONFIG_ERROR_OPTION_DEV_MISSING = "VPN_CONFIG_ERROR_OPTION_DEV_MISSING";
    static final String VPN_CONFIG_ERROR_OPTION_DEV_TUN_SUPPORTED = "VPN_CONFIG_ERROR_OPTION_DEV_TUN_SUPPORTED";
    static final String VPN_CONFIG_ERROR_OPTION_REMOTE_MISSING = "VPN_CONFIG_ERROR_OPTION_REMOTE_MISSING";
    static final String VPN_CONFIG_ERROR_OPTION_MISSING_FILE_REFERENCE = "VPN_CONFIG_ERROR_OPTION_MISSING_FILE_REFERENCE";

    private static final String AUTH_USER_PASS_OPTION_NAME = "auth-user-pass";

    private final Set<String> optionsBlacklist;
    private final Set<String> optionsWhitelist;
    private final Set<String> optionsInline;
    private final Map<String, Option> defaultOptionsByName;
    private final Provider<OpenVpnConfigurationParser> parserProvider;

    public enum OptionState { ACTIVE, BLACKLISTED, IGNORED, FILE_REQUIRED }

    @Inject
    public OpenVpnConfigurator(@Named("openvpn.configuration.options.blacklist") String optionsBlacklist,
                               @Named("openvpn.configuration.options.whitelist") String optionsWhitelist,
                               @Named("openvpn.configuration.options.file") String optionsInline,
                               @Named("openvpn.configuration.default-options") String defaultOpenVpnConfiguration,
                               Provider<OpenVpnConfigurationParser> parserProvider) throws OpenVpnConfigurationParser.ParseException {
        this.optionsBlacklist = ResourceHandler.readLinesAsSet(new SimpleResource(optionsBlacklist));
        this.optionsWhitelist = ResourceHandler.readLinesAsSet(new SimpleResource(optionsWhitelist));
        this.parserProvider = parserProvider;
        this.optionsInline = readInlineOptions(optionsInline);
        defaultOptionsByName = readDefaultOptions(defaultOpenVpnConfiguration);
    }

    public OpenVpnConfiguration createConfiguration(String config) throws OpenVpnConfigurationParser.ParseException {
        List<Option> userOptions = parseConfig(config);

        OpenVpnConfiguration configuration = new OpenVpnConfiguration();
        configuration.setSourceConfig(config);
        configuration.setUserOptions(userOptions);
        return configuration;
    }

    public boolean credentialsRequired(OpenVpnConfiguration configuration) {
        return configuration.getUserOptions().stream().anyMatch(this::isAuthOptionWithoutFileReference);
    }

    public Map<OptionState, Set<Option>> getUserOptionsByState(List<Option> userOptions) {
        Map<OptionState, Set<Option>> userOptionsByState = new EnumMap<>(OptionState.class);
        userOptionsByState.put(OptionState.ACTIVE, Collections.emptySet());
        userOptionsByState.put(OptionState.BLACKLISTED, Collections.emptySet());
        userOptionsByState.put(OptionState.FILE_REQUIRED, Collections.emptySet());
        userOptionsByState.put(OptionState.IGNORED, Collections.emptySet());

        userOptionsByState.putAll(userOptions.stream()
            .flatMap( o -> {
                if (o instanceof OptionsGroup) {
                    return Stream.concat(Stream.of(o), ((OptionsGroup)o).getOptions().stream());
                }
                return Stream.of(o);
            })
            .collect(Collectors.groupingBy(o -> {
                if (isBlacklisted(o)) {
                    return OptionState.BLACKLISTED;
                } else if (isIgnored(o)) {
                    return OptionState.IGNORED;
                } else if (isFileRequired(o)) {
                    return OptionState.FILE_REQUIRED;
                }
                return OptionState.ACTIVE;
            }, Collectors.toSet())));
        return userOptionsByState;
    }

    public List<Option> getActiveConfiguration(OpenVpnConfiguration configuration, String credentialFile, Map<String, String> optionFileByOption) {
        Map<OptionState, Set<Option>> optionsByState = getUserOptionsByState(configuration.getUserOptions());

        // include all user options which are neither blacklisted or ignored
        List<Option> options = filterOptions(configuration.getUserOptions(), optionsByState.get(OptionState.ACTIVE));

        // set names of external files
        optionsByState.get(OptionState.FILE_REQUIRED)
            .forEach(o -> {
                if (o instanceof SimpleOption) {
                    SimpleOption so = (SimpleOption) o;
                    if (so.getArguments() != null) {
                        String[] arguments = so.getArguments().clone();
                        arguments[0] = optionFileByOption.get(o.getName());
                        setOption(options, new SimpleOption(o.getLineNumber(), o.getName(), arguments));
                    }
                }
            });

        // merge eBlocker options
        defaultOptionsByName.values().forEach(o -> setOption(options, o));

        // change static tun device configuration to dynamic one
        if (configuration.getUserOptions().stream().anyMatch(o ->
            "dev".equals(o.getName())
                && o instanceof SimpleOption
                && ((SimpleOption) o).getArguments().length == 1
                && ((SimpleOption) o).getArguments()[0].matches("tun.+"))) {
            setOption(options, new SimpleOption(-1, "dev", new String[] { "tun" }));
        }

        // override auth-user-pass option if present
        if (configuration.getUserOptions().stream().anyMatch(o -> AUTH_USER_PASS_OPTION_NAME.equals(o.getName()))) {
            setOption(options, new SimpleOption(-1, AUTH_USER_PASS_OPTION_NAME, new String[]{ credentialFile }));
        }

        return options;
    }

    private List<Option> filterOptions(List<Option> userOptions, Set<Option> activeOptions) {
        return userOptions.stream()
            .filter(activeOptions::contains)
            .map(option -> {
                if (!(option instanceof OptionsGroup)) {
                    return option;
                }

                OptionsGroup optionsGroup = (OptionsGroup) option;
                List<Option> filteredOptions = filterOptions(optionsGroup.getOptions(), activeOptions);
                if (filteredOptions.equals(optionsGroup.getOptions())) {
                    return optionsGroup;
                }
                return new OptionsGroup(optionsGroup.getLineNumber(), optionsGroup.getName(), filteredOptions);
            })
            .collect(Collectors.toList());
    }

    public Collection<Option> getEblockerOptions() {
        return Collections.unmodifiableCollection(defaultOptionsByName.values());
    }

    /**
     * Checks if configuration fulfils minimal requirements for an openvpn configuration.
     * @param configuration Configuration to check.
     * @return A list containing message keys for invalid configurations. Will be empty if configuration seems ok.
     */
    public List<String> validateConfiguration(OpenVpnConfiguration configuration) {
        List<String> errorKeys = new ArrayList<>();
        addNonNull(errorKeys, validateClientOption(configuration.getUserOptions()));
        addNonNull(errorKeys, validateDevOption(configuration.getUserOptions()));
        addNonNull(errorKeys, validateRemoteOption(configuration.getUserOptions()));
        addNonNull(errorKeys, validateInlineOptions(configuration.getUserOptions()));
        return errorKeys;
    }

    private void addNonNull(List<String> values, String value) {
        if (value != null) {
            values.add(value);
        }
    }

    private String validateClientOption(List<Option> options) {
        // client or pull and tls-client options are required
        Optional<Option> clientOption = findOptionByName(options, "client");
        Optional<Option> pullOption = findOptionByName(options, "pull");
        Optional<Option> tlsClientOption = findOptionByName(options, "tls-client");

        if (!clientOption.isPresent() && (!pullOption.isPresent() || !tlsClientOption.isPresent())) {
            return VPN_CONFIG_ERROR_OPTION_CLIENT_MISSING;
        }

        return null;
    }

    private String validateDevOption(List<Option> options) {
        // dev tun is required
        Option option = findOptionByName(options, "dev").orElse(null);
        if (option == null) {
            return VPN_CONFIG_ERROR_OPTION_DEV_MISSING;
        } else if (!(option instanceof SimpleOption)
                || ((SimpleOption)option).getArguments() == null
                || !((SimpleOption)option).getArguments()[0].matches("tun.*")) {
            return VPN_CONFIG_ERROR_OPTION_DEV_TUN_SUPPORTED;
        }
        return null;
    }

    private String validateRemoteOption(List<Option> options) {
        // either a global remote option is needed ...
        if (findValidRemoteOption(options)) {
            return null;
        }

        // ... or each connection profile contains one
        List<OptionsGroup> connectionOptions = options.stream()
            .filter(o -> "connection".equals(o.getName()))
            .filter(o -> o instanceof OptionsGroup)
            .map(o -> (OptionsGroup) o)
            .collect(Collectors.toList());
        if (connectionOptions.isEmpty()) {
            return VPN_CONFIG_ERROR_OPTION_REMOTE_MISSING;
        }

        if (connectionOptions.stream()
            .anyMatch(o -> !findValidRemoteOption(o.getOptions()))) {
            return VPN_CONFIG_ERROR_OPTION_REMOTE_MISSING;
        }

        return null;
    }

    private boolean findValidRemoteOption(List<Option> options) {
        Option option = findOptionByName(options, "remote").orElse(null);
        return option instanceof SimpleOption && ((SimpleOption)option).getArguments() != null;
    }

    private String validateInlineOptions(List<Option> options) {
        // options which can be inlined must either be of type InlineOption or reference a file
        boolean missingFileReference = options.stream()
            .filter(o -> this.optionsInline.contains(o.getName()))
            .filter(o -> o instanceof SimpleOption)
            .map(o -> (SimpleOption) o)
            .anyMatch(o -> o.getArguments() == null);

        return missingFileReference ? VPN_CONFIG_ERROR_OPTION_MISSING_FILE_REFERENCE : null;
    }

    private Map<String, Option> readDefaultOptions(String defaultOpenVpnConfigFile) throws OpenVpnConfigurationParser.ParseException {
        String defaultConfig = ResourceHandler.load(new SimpleResource(defaultOpenVpnConfigFile));
        List<Option> options = parseConfig(defaultConfig);
        return options.stream().collect(Collectors.toMap(
                Option::getName,
                Function.identity(),
                (u, v) -> {
                    throw new IllegalStateException();
                },
                LinkedHashMap::new));
    }

    private Set<String> readInlineOptions(String inlineOptionsFile) {
        return ResourceHandler.readLinesAsSet(new SimpleResource(inlineOptionsFile)).stream()
            .map(l -> l.split(";")[0])
            .collect(Collectors.toSet());
    }

    private List<Option> parseConfig(String config) throws OpenVpnConfigurationParser.ParseException {
        OpenVpnConfigurationParser parser = parserProvider.get();
        return parser.parse(config);
    }

    private boolean isBlacklisted(Option option) {
        return optionsBlacklist.contains(option.getName());
    }

    private boolean isIgnored(Option option) {
        return !isWhiteListed(option) && !isDefaultOption(option) || isSpecialInlineOption(option);
    }

    private boolean isSpecialInlineOption(Option option) {
        return optionsInline.contains(option.getName())
            && option instanceof SimpleOption
            && ((SimpleOption)option).getArguments() != null && ((SimpleOption)option).getArguments().length >= 1
            && "[inline]".equals(((SimpleOption)option).getArguments()[0]);
    }

    private boolean isWhiteListed(Option option) {
        return optionsWhitelist.contains(option.getName());
    }

    private boolean isDefaultOption(Option option) {
        return defaultOptionsByName.containsKey(option.getName());
    }

    private boolean isFileRequired(Option option) {
        return optionsInline.contains(option.getName()) && !(option instanceof InlineOption) || isAuthOptionWithFileReference(option);
    }

    private boolean isAuthOptionWithFileReference(Option option) {
        if (!(option instanceof SimpleOption)) {
            return false;
        }
        SimpleOption simpleOption = (SimpleOption) option;
        return AUTH_USER_PASS_OPTION_NAME.equals(simpleOption.getName())
                && simpleOption.getArguments() != null
                && ((SimpleOption)option).getArguments().length == 1;
    }

    private boolean isAuthOptionWithoutFileReference(Option option) {
        if (!(option instanceof SimpleOption)) {
            return false;
        }
        SimpleOption simpleOption = (SimpleOption) option;
        return AUTH_USER_PASS_OPTION_NAME.equals(option.getName())
                && (simpleOption.getArguments() == null || simpleOption.getArguments().length == 0);
    }

    private void setOption(List<Option> options, Option option) {
        String name = option.getName();
        Integer i = findOptionIndexByName(options, name);
        if (i != null) {
            if (!option.equals(options.get(i))) {
                options.set(i, option);
            }
        } else {
            options.add(option);
        }

        // remove others of the same name:
        options.removeIf(o -> name.equals(o.getName()) && !option.equals(o));
    }

    private Integer findOptionIndexByName(List<Option> options, String name) {
        for(int i = 0; i < options.size(); ++i) {
            if (options.get(i).getName().equals(name)) {
                return i;
            }
        }
        return null;
    }

    private Optional<Option> findOptionByName(List<Option> options, String name) {
        return options.stream().filter(o -> o.getName().equals(name)).findFirst();
    }
}
