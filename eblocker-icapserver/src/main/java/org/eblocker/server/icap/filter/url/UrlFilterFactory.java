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

import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.icap.filter.FilterPriority;
import org.eblocker.server.icap.filter.FilterType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class UrlFilterFactory {
	private static final Logger log = LoggerFactory.getLogger(UrlFilterFactory.class);

	public static UrlFilterFactory getInstance() {
		return new UrlFilterFactory();
	}

	private FilterPriority priority = FilterPriority.MEDIUM;

	private String definition = null;

	private String domain = null;

	private StringMatchType matchType = null;

	private String matchString = null;

	private FilterType type = null;

	private DomainExcluder referrerDomainWhiteList = null;
	private DomainExcluder referrerDomainBlackList = null;

	private String redirectParam = null;

	private Boolean thirdParty = null;
	private Boolean document = null;
	private Boolean image = null;
	private Boolean script = null;
	private Boolean stylesheet = null;
	private Boolean subDocument = null;
	private String contentSecurityPolicies = null;

	public UrlFilterFactory setPriority(FilterPriority priority) {
		this.priority = priority;
		return this;
	}

	public UrlFilterFactory setDefinition(String definition) {
		this.definition = definition;
		return this;
	}

	public UrlFilterFactory setDomain(String domain) {
		this.domain = domain;
		return this;
	}

	public UrlFilterFactory setStringMatchType(StringMatchType matchType) {
		this.matchType = matchType;
		return this;
	}

	public UrlFilterFactory setMatchString(String matchString) {
		this.matchString = matchString;
		return this;
	}

	public UrlFilterFactory setType(FilterType type) {
		this.type = type;
		return this;
	}

	public UrlFilterFactory setThirdParty(Boolean thirdParty) {
		this.thirdParty = thirdParty;
		return this;
	}

	public UrlFilterFactory setDocument(Boolean document) {
		this.document = document;
		return this;
	}

	public UrlFilterFactory setImage(Boolean image) {
		this.image = image;
		return this;
	}

	public UrlFilterFactory setScript(Boolean script) {
		this.script = script;
		return this;
	}

	public UrlFilterFactory setStylesheet(Boolean stylesheet) {
		this.stylesheet = stylesheet;
		return this;
	}

	public UrlFilterFactory setSubDocument(Boolean subDocument) {
		this.subDocument = subDocument;
		return this;
	}

	public UrlFilterFactory setReferrerDomainWhiteList(List<String> domains) {
		if (domains == null || domains.isEmpty()) {
			referrerDomainWhiteList = null;
		} else {
			referrerDomainWhiteList = new DomainExcluder(domains, true);
		}
		return this;
	}

	public UrlFilterFactory setReferrerDomainBlackList(List<String> domains) {
		if (domains == null || domains.isEmpty()) {
			referrerDomainBlackList = null;
		} else {
			referrerDomainBlackList = new DomainExcluder(domains, false);
		}
		return this;
	}

	public UrlFilterFactory setRedirectParam(String redirectParam) {
		this.redirectParam = redirectParam;
		return this;
	}

    public UrlFilterFactory setContentSecurityPolicies(String contentSecurityPolicies) {
        this.contentSecurityPolicies = contentSecurityPolicies;
        return this;
    }

    public UrlFilter build() {
		if (type == null) {
			log.error("Cannot create URL filter without type [{}]", definition);
			return null;
		}

		UrlFilter filter = null;
		try {
			filter = new UrlFilter(priority, definition, domain, matchType, matchString, thirdParty, contentSecurityPolicies);
		} catch (EblockerException e) {
			log.error("Cannot create filter from definition [{}]", definition, e);
		}

		if (filter != null) {
			filter.setType(type);
			filter.setRedirectParam(redirectParam);
			if (referrerDomainWhiteList != null) {
				filter.addReferrerDomainExcluder(referrerDomainWhiteList);
			}
			if (referrerDomainBlackList != null) {
				filter.addReferrerDomainExcluder(referrerDomainBlackList);
			}
			addContentTypeRestrictions(filter);
		}

 		return filter;
	}

	private void addContentTypeRestrictions(UrlFilter filter) {
		List<ContentType> matchingContentTypes = new ArrayList<>();
		List<ContentType> nonMatchingContentTypes = new ArrayList<>();
		addContentType(ContentType.DOCUMENT, document, matchingContentTypes, nonMatchingContentTypes);
		addContentType(ContentType.IMAGE, image, matchingContentTypes, nonMatchingContentTypes);
		addContentType(ContentType.SCRIPT, script, matchingContentTypes, nonMatchingContentTypes);
		addContentType(ContentType.STYLESHEET, stylesheet, matchingContentTypes, nonMatchingContentTypes);
		addContentType(ContentType.SUB_DOCUMENT, subDocument, matchingContentTypes, nonMatchingContentTypes);
		if (!matchingContentTypes.isEmpty()) {
			filter.setMatchingContentTypes(matchingContentTypes);
		}
		if (!nonMatchingContentTypes.isEmpty()) {
			filter.setNonMatchingContentTypes(nonMatchingContentTypes);
		}
	}

	private void addContentType(ContentType type, Boolean match, List<ContentType> matchingContentTypes, List<ContentType> nonMatchingContentTypes) {
		if (match == null) {
			return;
		}
		if (match) {
			matchingContentTypes.add(type);
		} else {
			nonMatchingContentTypes.add(type);
		}
	}

}
