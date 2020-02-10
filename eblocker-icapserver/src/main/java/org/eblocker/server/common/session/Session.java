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
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionContext;
import com.fasterxml.jackson.annotation.JsonAutoDetect;

@JsonAutoDetect
public interface Session {

	String getSessionId();

	String getUserAgent();

	String getOutgoingUserAgent();

	void setOutgoingUserAgent(String userAgent);

	UserAgent getCustomUserAgent();

	void setCustomUserAgent(String userAgent);

	//String getEditedUserAgent();

	//void setEditedUserAgent(String editedUserAgent);

	IpAddress getIp();

	String getShortId();

	Integer getUserId();

	String getDeviceId();

	String getAppId();

	void setAppId(String appId);

	void markUsed();

	Decision popForwardDecision(String url);

	void addForwardDecision(String url, Decision decison);

	PageContext createPageContext(PageContext parentContext, String url);

	PageContext getPageContext(String url);

	boolean isBlockAds();

	void setBlockAds(boolean blockAds);

	int getBlockedAds();

	void incrementBlockedAds(TransactionContext transaction);

	boolean isBlockTrackings();

	void setBlockTrackings(boolean blockTrackings);

	int getBlockedTrackings();

	void incrementBlockedTrackings(TransactionContext transaction);

	boolean isUseDomainWhiteList();

	void setUseDomainWhiteList(boolean useDomainWhiteList);

	boolean isTorWorking();

	void setTorIsWorking(boolean working);

	/**
	 * All the fields that represent an error, or warning state should be combined here to show a warning icon on the eBlocker icon
	 * @return
     */
	boolean isWarningState();

	boolean isWhatIfMode();

	void setWhatIfMode(boolean whatIfMode);

	UserAgentInfo getUserAgentInfo();

    boolean isPatternFiltersEnabled();

    void setPatternFiltersEnabled(boolean patternFiltersEnabled);
}
