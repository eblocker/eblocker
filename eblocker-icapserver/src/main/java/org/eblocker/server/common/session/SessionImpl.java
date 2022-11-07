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
package org.eblocker.server.common.session;

import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.UserAgent;
import org.eblocker.server.common.page.PageContext;
import org.eblocker.server.common.page.PageContexts;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.common.util.UrlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class SessionImpl implements Session {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(SessionImpl.class);

    private final String sessionId;
    private Date lastUsed;

    private final String userAgent;
    private final IpAddress ip;
    private final Integer userId;
    private final String deviceId;
    private final UserAgentInfo userAgentInfo;

    private String appId;
    private String outgoingUserAgent = null;
    private UserAgent customUserAgent = null;

    private boolean isUsingTor;
    private boolean torError;

    private boolean patternFiltersEnabled;

    private boolean blockAds;
    private int blockedAds;

    private boolean blockTrackings;
    private int blockedTrackings;

    private boolean useDomainWhiteList;

    private ForwardDecisionStore forwardDecisions;
    private PageContexts pageContexts;

    private boolean whatIfMode = false;

    protected SessionImpl(String sessionId, String userAgent, IpAddress ip, String deviceId, Integer userId, UserAgentInfo userAgentInfo) {

        this.sessionId = sessionId;
        this.lastUsed = new Date();

        this.userAgent = userAgent;
        this.userAgentInfo = userAgentInfo == null ? UserAgentInfo.getDefault() : userAgentInfo;
        //this.editedUserAgent = userAgent;
        this.ip = ip;
        this.userId = userId;

        this.deviceId = deviceId;
        this.appId = null;

        this.isUsingTor = false;

        this.blockAds = true;
        this.blockedAds = 0;

        this.blockTrackings = true;
        this.blockedTrackings = 0;

        this.useDomainWhiteList = true;

        this.forwardDecisions = new ForwardDecisionStore();
        this.pageContexts = new PageContexts(128);

    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String getShortId() {
        return sessionId.substring(0, 8);
    }

    public Date getLastUsed() {
        return lastUsed;
    }

    @Override
    public void markUsed() {
        lastUsed = new Date();
    }

    @Override
    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public IpAddress getIp() {
        return ip;
    }

    @Override
    public Integer getUserId() {
        return userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    @Override
    public String getOutgoingUserAgent() {
        return outgoingUserAgent;
    }

    @Override
    public void setOutgoingUserAgent(String outgoingUserAgent) {
        this.outgoingUserAgent = outgoingUserAgent;
    }

    @Override
    public Decision popForwardDecision(String url) {
        return forwardDecisions.popDecision(url);
    }

    @Override
    public void addForwardDecision(String url, Decision decision) {
        forwardDecisions.addDecision(url, decision);
    }

    @Override
    public PageContext createPageContext(PageContext parentContext, String url) {
        return pageContexts.add(parentContext, UrlUtils.getOrigin(url), ip);
    }

    @Override
    public PageContext getPageContext(String url) {
        return pageContexts.get(UrlUtils.getOrigin(url));
    }

    @Override
    public boolean isBlockAds() {
        return blockAds;
    }

    @Override
    public void setBlockAds(boolean blockAds) {
        this.blockAds = blockAds;
    }

    @Override
    public int getBlockedAds() {
        return blockedAds;
    }

    @Override
    public void incrementBlockedAds(TransactionContext transaction) {
        blockedAds++;

        PageContext pageContext = getPageContext(transaction.getReferrer());
        if (pageContext != null) {
            pageContext.incrementBlockedAds(transaction.getUrl());
        }
		/*else{
			createPageContext(transaction.getReferrer());
		}*/
    }

    @Override
    public boolean isBlockTrackings() {
        return blockTrackings;
    }

    @Override
    public void setBlockTrackings(boolean blockTrackings) {
        this.blockTrackings = blockTrackings;
    }

    @Override
    public int getBlockedTrackings() {
        return blockedTrackings;
    }

    @Override
    public void incrementBlockedTrackings(TransactionContext transaction) {
        blockedTrackings++;
        PageContext pageContext = getPageContext(transaction.getReferrer());
        if (pageContext != null) {
            pageContext.incrementBlockedTrackings(transaction.getUrl());
        }
    }

    @Override
    public boolean isUseDomainWhiteList() {
        return useDomainWhiteList;
    }

    @Override
    public void setUseDomainWhiteList(boolean useDomainWhiteList) {
        this.useDomainWhiteList = useDomainWhiteList;
    }

    @Override
    public void setTorIsWorking(boolean working) {
        torError = working;
    }

    @Override
    public boolean isTorWorking() {
        return torError;
    }

    @Override
    public boolean isWarningState() {
        /*  TODO:
         * Add all the fields, that represent errors or warning here
         */
        return isTorWorking();
    }

    @Override
    public boolean isWhatIfMode() {
        return whatIfMode;
    }

    @Override
    public void setWhatIfMode(boolean whatIfMode) {
        this.whatIfMode = whatIfMode;
    }

    @Override
    public UserAgentInfo getUserAgentInfo() {
        return userAgentInfo;
    }

    @Override
    public UserAgent getCustomUserAgent() {
        return this.customUserAgent;
    }

    @Override
    public void setCustomUserAgent(String userAgent) {
        this.customUserAgent = new UserAgent(userAgent);
    }

    @Override
    public boolean isPatternFiltersEnabled() {
        return patternFiltersEnabled;
    }

    @Override
    public void setPatternFiltersEnabled(boolean patternFiltersEnabled) {
        this.patternFiltersEnabled = patternFiltersEnabled;
    }
}
