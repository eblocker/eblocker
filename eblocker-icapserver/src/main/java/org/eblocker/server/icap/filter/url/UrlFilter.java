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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.icap.filter.AbstractFilter;
import org.eblocker.server.icap.filter.FilterPriority;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.filter.FilterType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class UrlFilter extends AbstractFilter {
    private static final Logger log = LoggerFactory.getLogger(UrlFilter.class);

    @JsonProperty("type")
    private FilterType type;

    @JsonProperty("domain")
    private String domain;

    @JsonProperty("matchType")
    private StringMatchType matchType;

    @JsonProperty("matchString")
    private String matchString;

    @JsonProperty("referrerDomainExcluders")
    private List<DomainExcluder> referrerDomainExcluders;

    @JsonProperty("redirectParam")
    private String redirectParam;

    @JsonProperty("thirdParty")
    private Boolean thirdParty;

    @JsonProperty("matchingContentTypes")
    private List<ContentType> matchingContentTypes;

    @JsonProperty("nonMatchingContentTypes")
    private List<ContentType> nonMatchingContentTypes;

    @JsonProperty("contentSecurityPolicies")
    private String contentSecurityPolicies;

    private Pattern pattern;

    protected UrlFilter(@JsonProperty("priority") FilterPriority priority,
                        @JsonProperty("definition") String definition,
                        @JsonProperty("domain") String domain,
                        @JsonProperty("matchType") StringMatchType matchType,
                        @JsonProperty("matchString") String matchString,
                        @JsonProperty("thirdParty") Boolean thirdParty,
                        @JsonProperty("contentSecurityPolicies") String contentSecurityPolicies) {
        super(priority, definition);
        this.domain = domain;
        this.matchType = matchType;
        this.matchString = matchString;
        this.thirdParty = thirdParty;
        this.contentSecurityPolicies = contentSecurityPolicies;
        if (matchType == StringMatchType.REGEX) {
            try {
                pattern = Pattern.compile(matchString);
            } catch (PatternSyntaxException e) {
                String msg = "Invalid regular expression in filter definition [" + matchString + "]: " + e.getMessage();
                log.warn(msg);
                throw new EblockerException(msg);
            }
        }
    }

    @Override
    protected FilterResult doFilter(TransactionContext context) {
        if (thirdParty != null && context.getReferrer() != null) {
            if (!thirdParty.equals(context.isThirdParty())) {
                return FilterResult.noDecision(this);
            }
        }
        //
        // Check, if we can exclude this request, because the domain of the referrer
        // IS NOT on the (existing) white list, or
        // IS on the (existing) black list.
        //
        if (referrerDomainExcluders != null) {
            for (DomainExcluder referrerDomainExcluder : referrerDomainExcluders) {
                if (referrerDomainExcluder != null && referrerDomainExcluder.isExcluded(context.getReferrerHostname())) {
                    return FilterResult.noDecision(this);
                }
            }
        }
        //
        // Check any content type restrictions
        if (matchingContentTypes != null && !matchContentType(matchingContentTypes, context) ||
            nonMatchingContentTypes != null && matchContentType(nonMatchingContentTypes, context)) {
            return FilterResult.noDecision(this);
        }

        String url = context.getUrl();
        if (!isUrlMatching(url)) {
            // Filter doesn't match - cannot decide.
            return FilterResult.noDecision(this);
        }

        switch (type) {

            case PASS:
                // Filter is exception rule: Return explicit PASS
                return FilterResult.pass(this);

            case BLOCK:
                // Filter without csp just blocks
                return contentSecurityPolicies == null ? FilterResult.block(this) : FilterResult.setCspHeader(this, contentSecurityPolicies);

            case REDIRECT:
                // Filter suggest to redirect to other target.
                return FilterResult.redirect(this, getRedirectTarget(context));

            case ASK:
                // Let user decide, what to do.
                return FilterResult.ask(this, getRedirectTarget(context));

            case NO_CONTENT:
                // Let user decide, what to do.
                return FilterResult.noContent(this);
        }

        return null;
    }

    private String getRedirectTarget(TransactionContext context) {
        return UrlUtils.findUrlParameter(context.getUrl(), redirectParam);
    }

    public String getContentSecurityPolicies() {
        return contentSecurityPolicies;
    }

    protected boolean isUrlMatching(String url) {
        switch (matchType) {
            case REGEX:
                return pattern.matcher(url).find();
            case DOMAIN:
                String hostname = UrlUtils.getHostname(url);
                return hostname.equals(domain) || hostname.endsWith("." + domain);
            case STARTSWITH:
                return url.startsWith(matchString);
            case ENDSWITH:
                return url.endsWith(matchString);
            case EQUALS:
                return url.equals(matchString);
            case CONTAINS:
            default:
                return url.contains(matchString);
        }
    }


    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(super.toString()).append("\t").append(type).append("\t").append(matchType).append("\t").append(matchString);
        s.append("\t").append(redirectParam == null ? "-" : redirectParam);
        if (referrerDomainExcluders == null || referrerDomainExcluders.size() < 1) {
            s.append("\t-\t-");
        } else {
            s.append("\t").append(referrerDomainExcluders.get(0));
            if (referrerDomainExcluders.size() >= 2) {
                s.append("\t").append(referrerDomainExcluders.get(1));
            } else {
                s.append("\t-");
            }
        }
        return s.toString();
    }

    public void setType(FilterType type) {
        this.type = type;
    }

    public void addReferrerDomainExcluder(DomainExcluder referrerDomainExcluder) {
        if (referrerDomainExcluders == null) {
            referrerDomainExcluders = new ArrayList<>();
        }
        referrerDomainExcluders.add(referrerDomainExcluder);
    }

    public void setRedirectParam(String redirectParam) {
        this.redirectParam = redirectParam;
    }

    public void setMatchingContentTypes(List<ContentType> matchingContentTypes) {
        this.matchingContentTypes = matchingContentTypes;
    }

    public void setNonMatchingContentTypes(List<ContentType> nonMatchingContentTypes) {
        this.nonMatchingContentTypes = nonMatchingContentTypes;
    }

    private boolean matchContentType(List<ContentType> contentTypes, TransactionContext context) {
        return contentTypes.stream()
            .map(ContentType::matcher)
            .filter(m -> m.matches(context.getAccept(), context.getUrl()))
            .findAny()
            .isPresent();
    }

    @JsonIgnore(false)
    @Override
    public String getDomain() {
        return domain;
    }
}
