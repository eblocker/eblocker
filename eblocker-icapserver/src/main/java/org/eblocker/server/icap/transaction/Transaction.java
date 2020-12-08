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

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import org.eblocker.server.common.page.PageContext;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.common.transaction.TransactionIdentifier;
import org.eblocker.server.icap.filter.FilterResult;

public interface Transaction extends TransactionContext, TransactionIdentifier {

    Session getSession();

    void setSession(Session session);

    boolean isRequest();

    boolean isResponse();

    FullHttpRequest getRequest();

    FullHttpResponse getResponse();

    void setRequest(FullHttpRequest httpRequest);

    /**
     * This method does not only set the http response object -
     * it will make the whole transaction a response transaction,
     * even if it was a request transaction, before!
     *
     * @param httpResponse
     */
    void setResponse(FullHttpResponse httpResponse);

    String getUserReference();

    String getContentType();

    boolean isPreview();

    boolean isContentChanged();

    boolean isHeadersChanged();

    boolean isComplete();

    void setComplete(boolean complete);

    void block();

    void noContent();

    void redirect(String targetUrl);

    void setRedirectTarget(String redirectTarget);

    void setDecision(Decision decision);

    String getBaseUrl();

    void setBaseUrl(String baseUrl);

    void setFilterResult(FilterResult result);

    FilterResult getFilterResult();

    void setContentChanged(boolean changed);

    void setHeadersChanged(boolean changed);

    PageContext getPageContext();

    void setPageContext(PageContext pageContext);

    Injections getInjections();

    void setInjections(Injections injections);

    ContentEncoding getContentEncoding();

    void setContentEncoding(ContentEncoding contentEncoding);

    StringBuilder getContent();

    void setContent(StringBuilder content);
}
