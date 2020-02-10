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
package org.eblocker.server.icap.filter.easylist;

import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterLineParser;
import org.eblocker.server.icap.filter.FilterType;
import org.eblocker.server.icap.filter.url.StringMatchType;
import org.eblocker.server.icap.filter.url.UrlFilterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EasyListLineParser implements FilterLineParser {

	public static final Logger log = LoggerFactory.getLogger(EasyListLineParser.class);

	private static final Pattern OPTIONS_PATTERN = Pattern.compile(
	    "~?(csp=|domain=|" +
        String.join("|", BooleanOption.allOptions()) +
        ").*"
    );

	private String definition;

	private EasyListRuleType ruleType;

	private String[] ruleGroups;

	private String domain;

	private String matchString;

	private boolean useRegex;

	private boolean domainOnly;

	protected String[] options = new String[]{};

    private List<String> referrerDomainWhiteList = new ArrayList<>();

    private List<String> referrerDomainBlackList = new ArrayList<>();

	protected FilterType type;

    private Map<BooleanOption, Boolean> booleanOptions = new EnumMap<>(BooleanOption.class);

    private String contentSecurityPolicies;

    public EasyListLineParser() {
	}

	@Override
	public Filter parseLine(String definition) {
		this.definition = definition;
		return doParse();
	}

	private Filter doParse() {
		if (definition == null) {
			return null;
		}

        //
        // A dollar sign indicates a list of comma separated options.
        // Remove these options from the matchString and split options into a String array.
        //
        String definitionWithoutOptions = definition;
        if (definition.contains("$")) {
            int dollar = definition.lastIndexOf('$');
            String potentialOptions = definition.substring(dollar+1);
            if (OPTIONS_PATTERN.matcher(potentialOptions).matches()) {
                options = potentialOptions.split(",");
                parseOptions();
                definitionWithoutOptions = definition.substring(0, dollar);
            }
        }

        for (EasyListRuleType easyListRuleType: EasyListRuleType.values()) {
			Matcher matcher = easyListRuleType.matcher(definitionWithoutOptions);
			if (matcher.matches()) {
				ruleType = easyListRuleType;
				ruleGroups = new String[matcher.groupCount()];
				for (int i = 0; i < ruleGroups.length; i++) {
					ruleGroups[i] = matcher.group(i+1);
				}
				break;
			}
		}

		if (ruleType == null) {
			log.warn("Found EASYLIST definition of unknown format, ignoring this line: [{}]", definition);
			return null;
		}


		switch (ruleType) {

		case COMMENT:
		case TITLE:
		case ELEMENT_HIDE:
		case ELEMENT_NOHIDE:
			// Silently skip these lines.
			return null;

		default:
			break;

		}

		type = ruleType.getType();

		normalizeMatchString();
		if (Boolean.TRUE.equals(booleanOptions.get(BooleanOption.WEB_SOCKET))) {
		    log.info("filtering websockets unsupported, dropping rule: {}", definition);
		    return null;
        }

		StringMatchType matchType;
		if (domainOnly) {
            matchType = StringMatchType.DOMAIN;
        } else if (useRegex) {
			matchString = ruleType.getRegexPrefix() + matchString + ruleType.getRegexPostfix();
			matchType = StringMatchType.REGEX;

		} else {
			matchType = ruleType.getSubstringMatchType();
		}

        UrlFilterFactory filterFactory = UrlFilterFactory.getInstance()
            .setPriority(ruleType.getPriority())
            .setDefinition(definition)
            .setDomain(domain)
            .setStringMatchType(matchType)
            .setMatchString(matchString)
            .setType(type)
            .setReferrerDomainWhiteList(referrerDomainWhiteList)
            .setReferrerDomainBlackList(referrerDomainBlackList)
            .setThirdParty(booleanOptions.get(BooleanOption.THIRD_PARTY))
            .setDocument(booleanOptions.get(BooleanOption.DOCUMENT))
            .setImage(booleanOptions.get(BooleanOption.IMAGE))
            .setScript(booleanOptions.get(BooleanOption.SCRIPT))
            .setStylesheet(booleanOptions.get(BooleanOption.STYLESHEET))
            .setSubDocument(booleanOptions.get(BooleanOption.SUB_DOCUMENT))
            .setContentSecurityPolicies(contentSecurityPolicies);

		return filterFactory
				.build();
	}

	private void normalizeMatchString() {
		matchString = ruleGroups[0];

		final boolean isRegex = ruleType.isExplicitRegularExpression();

        //
		// Remove trailing star. According to the EASYLIST rules, a trailing star has no meaning.
		// Unless there is a trailing pipe, a rule anyway implies a "*" at the end.
		//
		if (matchString.endsWith("*")) {
			matchString = matchString.substring(0, matchString.length()-1);
		}

		//
		// The following characters require us to use REGEX for the rule.
		// The characters themselves will be replaced later.
		// For now, we set only the flag.
		//
		if (matchString.startsWith("|") || matchString.contains("^") || matchString.contains("*")) {
			useRegex = true;
		}

		//
		// Deactivated following rules, as I do not understand them anymore.
		//
		/*
		if (matchString.contains("|*|")) {
			matchString = matchString.replaceAll("\\|\\*\\|", "*");
		}
		if (matchString.contains("|*")) {
			matchString = matchString.replaceAll("\\|\\*", "*");
		}
		*/

		//
		// Escape some single characters, which have a special meaning in REGEX patterns.
		//
		if (useRegex && !isRegex) {
			if (matchString.contains(".")) {
				matchString = matchString.replaceAll("\\.", "\\\\.");
			}
			if (matchString.contains("?")) {
				matchString = matchString.replaceAll("\\?", "\\\\?");
			}
			if (matchString.contains("[")) {
				matchString = matchString.replaceAll("\\[", "\\\\[");
			}
			if (matchString.contains("]")) {
				matchString = matchString.replaceAll("\\]", "\\\\]");
			}
			if (matchString.contains("(")) {
				matchString = matchString.replaceAll("\\(", "\\\\(");
			}
			if (matchString.contains(")")) {
				matchString = matchString.replaceAll("\\)", "\\\\)");
			}
		}

		//
		// A GLOB star has to be replaced with a REGEX dot-star.
		//
		if (!isRegex && matchString.contains("*")) {
			matchString = matchString.replaceAll("\\*", ".*");
		}

		//
		// A leading pipe indicates that the pattern starts with a basic hostname.
		// The rules matches http and https protocol, and an optional leading "www." in front of the basic hostname.
		//
		if (matchString.startsWith("|")) {
			domain = EasyListLineParserUtils.findDomain(ruleGroups[0].substring(1));
			if (domain == null) {
				log.info("Found no hostname in BEGIN or EXACT rule in filter {} with definition {}", matchString, definition);
			} else if (domain.contains("*")) {
			    // TODO: LearningFilter does not support domain wild cards (EB1-758)
				domain = null;
			}
			matchString = "http(s)?://([a-zA-Z0-9\\-]+\\.)*"+matchString.substring(1);
		}

		//
		// Escape remaining pipe characters, which have a special meaning in REGEX patterns.
		//
		if (useRegex && !isRegex) {
			if (matchString.contains("|")) {
				matchString = matchString.replaceAll("\\|", "\\\\|");
			}
		}

		//
		// The ^ indicates a separator character. Anything except letter, digit or _-.%
        //
        if (matchString.contains("^") && !isRegex) {
            int index = matchString.indexOf('^');
            if (domain != null && index == matchString.length() - 1) {
                matchString = null;
                domainOnly = true;
            } else {
                matchString = matchString.replaceAll("\\^", "([^a-zA-Z0-9_.%-]|\\$)");
            }
        }
	}

    private void parseOptions() {
        //
        // Parse (some) options.
        //
        for (String option: options) {
            if (option.startsWith("domain=")) {
                String[] domains = option.substring("domain=".length()).split("\\|");
                for (String referrerDomain : domains) {
                    if (referrerDomain.startsWith("~")) {
                        referrerDomainBlackList.add(referrerDomain.substring(1));
                    } else {
                        referrerDomainWhiteList.add(referrerDomain);
                    }
                }
            } else if (option.startsWith("csp=")) {
                contentSecurityPolicies = option.substring("csp=".length());
            } else {
                for(BooleanOption bOption : BooleanOption.values()) {
                    if (bOption.option().equals(option)) {
                        booleanOptions.put(bOption, Boolean.TRUE);
                    } else if (("~" + bOption.option()).equals(option)) {
                        booleanOptions.put(bOption, Boolean.FALSE);
                    }
                }
            }
        }
    }

    @Override
	public String toString() {
		StringBuilder s = new StringBuilder();

		s.append("EasyListRule ==> [\n");
		s.append("  type     : ").append(ruleType).append("\n");
		s.append("  filter   : ").append(definition).append("\n");
		s.append("  domain : ").append(domain).append("\n");
		s.append("]");

		return s.toString();
	}

	private enum BooleanOption {
		THIRD_PARTY("third-party"),
		SCRIPT("script"),
		IMAGE("image"),
		STYLESHEET("stylesheet"),
		OBJECT("object"),
		XML_HTTP_REQUEST("xmlhttprequest"),
		OBJECT_SUB_REQUEST("object-subrequest"),
		SUB_DOCUMENT("subdocument"),
		PING("ping"),
		WEB_SOCKET("websocket"),
		DOCUMENT("document"),
		ELEM_HIDE("elemhide"),
		GENERIC_HIDE("generichide"),
		GENERIC_BLOCK("genericblock"),
		OTHER("other"),
        COLLAPSE("collapse"),
        POPUP("popup"),
        WEBRTC("webrtc"),

        // Not documented at https://adblockplus.org/en/filters, but used in EasyLists:
        MEDIA("media"),
        FONT("font");


		private final String option;

		BooleanOption(String option) {
			this.option = option;
		}

		String option() {
			return option;
		}

		private static Set<String> allOptions() {
            return Arrays.stream(BooleanOption.values())
                .map(BooleanOption::option)
                .collect(Collectors.toSet());
        }
	}
}

