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
package org.eblocker.server.http.controller.impl;

import com.google.inject.Inject;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.network.BaseURLs;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.ImmutableTransactionContext;
import org.eblocker.server.common.transaction.TransactionCache;
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.common.transaction.TransactionIdentifier;
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.http.controller.RedirectController;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class RedirectControllerImpl implements RedirectController {

    private static final Logger log = LoggerFactory.getLogger(RedirectControllerImpl.class);

    private SessionStore sessionStore;
    private TransactionCache transactionCache;
    private DataSource dataSource;
    private BaseURLs baseUrls;

    @Inject
    public RedirectControllerImpl(SessionStore sessionStore, TransactionCache transactionCache, DataSource dataSource, BaseURLs baseUrls) {
        this.sessionStore = sessionStore;
        this.transactionCache = transactionCache;
        this.dataSource = dataSource;
        this.baseUrls = baseUrls;
    }

    @Override
    public Object read(Request request, Response response) {
        Session session = getSession(request);
        TransactionContext transaction = getTransactionContext(request, session);

        String redirect;
        switch (getDecision(request)) {

            case PASS:
                session.addForwardDecision(transaction.getUrl(), Decision.PASS);
                redirect = transaction.getUrl();
                break;
            case REDIRECT:
                redirect = transaction.getRedirectTarget();
                break;
            default:
                throw new NotFoundException("Invalid redirect decision: [" + request.getHeader("decision") + "]");
        }

        return redirect(response, redirect);
    }

    @Override
    public Object update(Request request, Response response) {
        Session session = getSession(request);
        TransactionContext transaction = getTransactionContext(request, session);

        dataSource.setRedirectDecision(session.getSessionId(), transaction.getDomain(), getDecision(request));

        return null;
    }

    @Override
    public Object delete(Request request, Response response) {
        Session session = getSession(request);
        String domain = getDomain(request);
        log.info("{}\t Removing user decision for redirects concerning tracker domain {}", session.getShortId(), domain);

        dataSource.setRedirectDecision(session.getSessionId(), domain, Decision.ASK);

        return null;
    }

    @Override
    public Object prepare(Request request, Response response) {
        Session session = getSession(request);

        String url = request.getHeader("url");
        if (url == null) {
            throw new EblockerException("Mandatory parameter 'url' is missing");
        }
        String originalDomain = UrlUtils.getDomain(UrlUtils.getHostname(url));

        String redirectTarget = request.getHeader("redirectTarget");
        String targetDomain = null;
        if (redirectTarget != null && !redirectTarget.isEmpty()) {
            targetDomain = UrlUtils.getDomain(UrlUtils.getHostname(redirectTarget));
        }

        UUID uuid = transactionCache.add(new ImmutableTransactionContext(
                session.getSessionId(),
                url,
                url,
                originalDomain,
                originalDomain,
                request.getHeader(HttpHeaders.Names.ACCEPT),
                Decision.NO_DECISION,
                redirectTarget,
                false
        ));
        log.info("\t Creating artificial transaction for {}", request.getHeader("url"));

        response.setContentType("text/html");

        /**
         * Note: we can not use request.getBaseUrl() here, because it never returns "https:"
         * See issue EB1-122.
         */
        String baseUrl = baseUrls.selectURLForPage(url);

        if (targetDomain == null) {
            return redirect(response, baseUrl + "/dashboard/#!/redirect/" + uuid.toString() + "/" + originalDomain);
        } else {
            return redirect(response, baseUrl + "/dashboard/#!/redirect/" + uuid.toString() + "/" + originalDomain + "/" + targetDomain);
        }
    }

    private Object redirect(Response response, String url) {
        response.addLocationHeader(url);
        response.setResponseCode(HttpResponseStatus.MOVED_PERMANENTLY.code());
        return null;
    }

    private String getDomain(Request request) {
        return request.getHeader("domain");
    }

    private Decision getDecision(Request request) {
        Decision decision;
        try {
            decision = Decision.valueOf(request.getHeader("decision"));
        } catch (IllegalArgumentException e) {
            String errorMsg = String.format("Invalid decision: [%s]", request.getHeader("decision"));
            log.error(errorMsg, e);
            throw new NotFoundException(errorMsg);
        }
        return decision;
    }

    private TransactionContext getTransactionContext(Request request, Session session) {
        String uuidString = request.getHeader("uuid");
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            String errorMsg = String.format("Invalid transaction id: [%s]", uuidString);
            log.error(errorMsg, e);
            throw new NotFoundException(errorMsg);
        }
        TransactionContext transaction = transactionCache.get(uuid);
        if (transaction == null) {
            throw new NotFoundException("Unknown transaction id: [" + uuid.toString() + "]");
        }
        return transaction;
    }

    // TODO: why not subclass SessionContextController?
    private Session getSession(Request request) {
        return sessionStore.getSession((TransactionIdentifier) request.getAttachment("transactionIdentifier"));
    }

}
