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
package org.eblocker.server.icap.transaction;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import org.eblocker.server.icap.transaction.processor.AdBlockerProcessor;
import org.eblocker.server.icap.transaction.processor.BpjmFilterProcessor;
import org.eblocker.server.icap.transaction.processor.CompressProcessor;
import org.eblocker.server.icap.transaction.processor.ContentSecurityPoliciesProcessor;
import org.eblocker.server.icap.transaction.processor.CustomDomainFilterWhitelistProcessor;
import org.eblocker.server.icap.transaction.processor.DecompressProcessor;
import org.eblocker.server.icap.transaction.processor.DomainWhiteListProcessor;
import org.eblocker.server.icap.transaction.processor.EBlockerFilterProcessor;
import org.eblocker.server.icap.transaction.processor.FinalizeProcessor;
import org.eblocker.server.icap.transaction.processor.ForwardDecisionProcessor;
import org.eblocker.server.icap.transaction.processor.HtmlInjectionProcessor;
import org.eblocker.server.icap.transaction.processor.IgnoreEBlockerProcessor;
import org.eblocker.server.icap.transaction.processor.InsertClientSslCheckProcessor;
import org.eblocker.server.icap.transaction.processor.InsertToolbarProcessor;
import org.eblocker.server.icap.transaction.processor.MalwareFilterProcessor;
import org.eblocker.server.icap.transaction.processor.MalwarePatternFilterProcessor;
import org.eblocker.server.icap.transaction.processor.PageContextProcessor;
import org.eblocker.server.icap.transaction.processor.PatternFilterStatisticsProcessor;
import org.eblocker.server.icap.transaction.processor.RedirectFromSetupPageProcessor;
import org.eblocker.server.icap.transaction.processor.ReferrerRemoveProcessor;
import org.eblocker.server.icap.transaction.processor.ResponseShortCutProcessor;
import org.eblocker.server.icap.transaction.processor.SessionProcessor;
import org.eblocker.server.icap.transaction.processor.SetBaseUrlProcessor;
import org.eblocker.server.icap.transaction.processor.SetDntHeaderProcessor;
import org.eblocker.server.icap.transaction.processor.SetInjectionsProcessor;
import org.eblocker.server.icap.transaction.processor.TrackingBlockerProcessor;
import org.eblocker.server.icap.transaction.processor.UserAgentSpoofProcessor;
import org.eblocker.server.icap.transaction.processor.WebRTCBlocker;
import org.eblocker.server.icap.transaction.processor.YoutubeAdBlocker;

import java.util.Arrays;
import java.util.List;

public class TransactionProcessorsModule extends AbstractModule {

    @Override
    protected void configure() {
    }

    @Provides
    @Named("requestProcessors")
    public List<TransactionProcessor> getRequestProcessors(AdBlockerProcessor adBlockerProcessor,
                                                           BpjmFilterProcessor bpjmFilterProcessor,
                                                           CustomDomainFilterWhitelistProcessor customDomainFilterWhitelistProcessor,
                                                           EBlockerFilterProcessor eBlockerFilterProcessor,
                                                           FinalizeProcessor finalizeProcessor,
                                                           ForwardDecisionProcessor forwardDecisionProcessor,
                                                           IgnoreEBlockerProcessor ignoreEBlockerProcessor,
                                                           MalwareFilterProcessor malwareFilterProcessor,
                                                           MalwarePatternFilterProcessor malwarePatternFilterProcessor,
                                                           PageContextProcessor pageContextProcessor,
                                                           PatternFilterStatisticsProcessor patternFilterStatisticsProcessor,
                                                           RedirectFromSetupPageProcessor redirectToInternalWebsiteProcessor,
                                                           ReferrerRemoveProcessor referrerRemoveProcessor,
                                                           SessionProcessor sessionProcessor,
                                                           SetBaseUrlProcessor setBaseUrlProcessor,
                                                           SetDntHeaderProcessor setDntHeaderProcessor,
                                                           TrackingBlockerProcessor trackingBlockerProcessor,
                                                           UserAgentSpoofProcessor userAgentSpoofProcessor) {
        return Arrays.asList(
                sessionProcessor,
                ignoreEBlockerProcessor,
                bpjmFilterProcessor,
                customDomainFilterWhitelistProcessor,
                pageContextProcessor,
                setBaseUrlProcessor,
                redirectToInternalWebsiteProcessor,
                malwareFilterProcessor,
                malwarePatternFilterProcessor,
                forwardDecisionProcessor,
                userAgentSpoofProcessor,
                patternFilterStatisticsProcessor,
                eBlockerFilterProcessor,
                adBlockerProcessor,
                trackingBlockerProcessor,
                referrerRemoveProcessor,
                setDntHeaderProcessor,
                finalizeProcessor
        );
    }

    @Provides
    @Named("responseProcessors")
    public List<TransactionProcessor> getResponseProcessors(CompressProcessor compressProcessor,
                                                            ContentSecurityPoliciesProcessor contentSecurityPoliciesProcessor,
                                                            DecompressProcessor decompressProcessor,
                                                            DomainWhiteListProcessor domainWhiteListProcessor,
                                                            FinalizeProcessor finalizeProcessor,
                                                            HtmlInjectionProcessor htmlInjectionProcessor,
                                                            IgnoreEBlockerProcessor ignoreEBlockerProcessor,
                                                            InsertClientSslCheckProcessor insertClientSslCheckProcessor,
                                                            InsertToolbarProcessor insertToolbarProcessor,
                                                            PageContextProcessor pageContextProcessor,
                                                            ResponseShortCutProcessor responseShortCutProcessor,
                                                            SessionProcessor sessionProcessor,
                                                            SetBaseUrlProcessor setBaseUrlProcessor,
                                                            SetInjectionsProcessor setInjectionsProcessor,
                                                            WebRTCBlocker webRTCBlocker,
                                                            YoutubeAdBlocker youtubeAdBlocker) {
        return Arrays.asList(
                sessionProcessor,
                ignoreEBlockerProcessor,
                pageContextProcessor,
                setBaseUrlProcessor,
                domainWhiteListProcessor,
                responseShortCutProcessor,
                contentSecurityPoliciesProcessor,
                decompressProcessor,
                webRTCBlocker,
                setInjectionsProcessor,
                insertToolbarProcessor,
                insertClientSslCheckProcessor,
                youtubeAdBlocker,
                htmlInjectionProcessor,
                compressProcessor,
                finalizeProcessor
        );
    }
}
