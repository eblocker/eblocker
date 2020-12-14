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
package org.eblocker.certificate.validator.squid;

import java.nio.charset.StandardCharsets;

public abstract class CertificateValidationMessage {

    private boolean useConcurrency = false;
    private Long id;
    protected String[] errorName;
    protected final String[] errorCertId;

    public CertificateValidationMessage(Long id, String[] errorName, String[] errorCertId, boolean useConcurrency) {
        this.id = id;
        this.errorName = errorName == null ? new String[]{} : errorName;
        this.errorCertId = errorCertId == null ? new String[]{} : errorCertId;
        this.useConcurrency = useConcurrency;
    }

    public Long getId() {
        return id;
    }

    public String[] getErrorName() {
        return errorName;
    }

    public String[] getErrorCertId() {
        return errorCertId;
    }

    protected abstract String getMessage();

    protected abstract String getContent();

    protected void appendContent(StringBuilder s) {
        for (int i = 0; i < errorName.length; i++) {
            s.append("error_name_")
                    .append(i)
                    .append("=")
                    .append(errorName[i])
                    .append("\n");
        }
        for (int i = 0; i < errorCertId.length; i++) {
            s.append("error_cert_")
                    .append(i)
                    .append("=")
                    .append(errorCertId[i])
                    .append("\n");
        }
    }

    @Override
    public String toString() {
        String content = getContent();

        StringBuilder s = new StringBuilder();

        if (useConcurrency) {//if using concurrency add the id to the response, otherwise dont do that
            s.append(id)
                    .append(" ");
        }

        s.append(getMessage())
                .append(" ")
                .append(content.getBytes(StandardCharsets.UTF_8).length)
                .append(" ")
                .append(content);

        return s.toString();
    }
}
