/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.http.security;

import org.restexpress.Request;
import org.restexpress.pipeline.Preprocessor;

public interface DashboardAuthorizationProcessor extends Preprocessor {
    String VERIFY_DEVICE_ID = "VERIFY_DEVICE_ID";
    String VERIFY_USER_ID = "VERIFY_USER_ID";
    String DEVICE_ID_KEY = "deviceId";
    String USER_ID_KEY = "userId";

    @Override
    public void process(Request request);
}
