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
package org.eblocker.server.http.exceptions.restexpress;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.restexpress.exception.ServiceException;

public class ServiceNotAvailableServiceException extends ServiceException {

    private static final long serialVersionUID = -8484662487466021563L;
    private static final HttpResponseStatus STATUS = HttpResponseStatus.SERVICE_UNAVAILABLE;

    public ServiceNotAvailableServiceException()
    {
        super(STATUS);
    }

    /**
     * @param message
     */
    public ServiceNotAvailableServiceException(String message)
    {
        super(STATUS, message);
    }

    /**
     * @param cause
     */
    public ServiceNotAvailableServiceException(Throwable cause)
    {
        super(STATUS, cause);
    }

    /**
     * @param message
     * @param cause
     */
    public ServiceNotAvailableServiceException(String message, Throwable cause)
    {
        super(STATUS, message, cause);
    }

}
