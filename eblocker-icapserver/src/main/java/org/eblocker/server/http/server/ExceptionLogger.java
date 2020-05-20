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
package org.eblocker.server.http.server;

import org.eblocker.server.http.exceptions.restexpress.ServiceNotAvailableServiceException;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.UnauthorizedException;
import org.restexpress.pipeline.Postprocessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExceptionLogger implements Postprocessor {
    private static final Logger log = LoggerFactory.getLogger(ExceptionLogger.class);

    @Override
    public void process(Request request, Response response) {
        if (!response.hasException()) {
            return;
        }

        Throwable e = response.getException();
        if (isExpectedException(request, response, e)) {
            return;
        }

        if(log.isDebugEnabled()) {
            // Log stack trace only in debug mode
            log.error(getLogMessage(request, response, e), e);
        } else {
            // Log as error, but w/o stack trace
            log.error(getLogMessage(request, response, e));
        }
    }

    private boolean isExpectedException(Request request, Response response, Throwable e) {
        // Do not log Unauthorized exceptions by default, as they are expected and quite frequent
        if (e instanceof UnauthorizedException) {
            String error = e.getMessage();
            if (error != null && (error.startsWith("error.token.") || error.startsWith("error.credentials."))) {
                if (log.isDebugEnabled()) {
                    log.debug(getLogMessage(request, response, e));
                }
                return true;
            }
        }

        // Do not log ServiceNotAvailable exceptions by default, as they are expected and may be frequent during restart
        if (e instanceof ServiceNotAvailableServiceException) {
            if (log.isDebugEnabled()) {
                log.debug(getLogMessage(request, response, e));
            }
            return true;
        }

        return false;
    }

    private String getLogMessage(Request request, Response response, Throwable e) {
        return "Returning " +
                response.getResponseStatus().code() + " " +
                response.getResponseStatus().reasonPhrase() + " for " +
                request.getHttpMethod().name() + " " +
                request.getUrl() + " caused by " +
                e.getClass().getName() + ": " + e.getMessage();
    }
}
