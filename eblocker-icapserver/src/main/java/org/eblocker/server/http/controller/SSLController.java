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
package org.eblocker.server.http.controller;

import io.netty.buffer.ByteBuf;
import org.eblocker.server.common.data.CaOptions;
import org.eblocker.server.common.data.Certificate;
import org.eblocker.server.common.data.DashboardSslStatus;
import org.eblocker.server.http.ssl.Suggestions;
import org.restexpress.Request;
import org.restexpress.Response;

import java.io.IOException;
import java.util.Map;

public interface SSLController {
    ByteBuf getCACertificateByteStream(Request request, Response response) throws IOException;

    ByteBuf getCACertificateByteStreamForFirefox(Request request, Response response) throws IOException;

    ByteBuf getRenewalCertificateByteStreamForFirefox(Request request, Response response) throws IOException;

    ByteBuf getRenewalCertificateByteStream(Request request, Response response) throws IOException;

    boolean areCertificatesReady(Request request, Response response);

    void setSSLState(Request request, Response response) throws IOException;

    boolean getSSLState(Request request, Response response);

    boolean setAutoTrustAppState(Request request, Response response) throws IOException;

    boolean getAutoTrustAppState(Request request, Response response);

    void removeWhitelistedUrl(Request request, Response resp) throws Exception;

    Integer removeAllWhitelistedUrl(Request request, Response resp) throws Exception;

    void addUrlToSSLWhitelist(Request request, Response resp) throws Exception;

    Certificate createNewRootCA(Request request, Response response);

    CaOptions getDefaultCaOptions(Request request, Response response);

    Certificate getRootCaCertificate(Request request, Response response);

    void markCertificateStatus(Request request, Response response);

    boolean getDeviceStatus(Request request, Response response);

    boolean setDeviceStatus(Request request, Response response);

    DashboardSslStatus getSslDashboardStatus(Request request, Response response);

    Suggestions getFailedConnections(Request request, Response response);

    void clearFailedConnections(Request request, Response response);

    Map<String, Boolean> getErrorRecordingEnabled(Request request, Response response);

    Map<String, Boolean> setErrorRecordingEnabled(Request request, Response response);
}
