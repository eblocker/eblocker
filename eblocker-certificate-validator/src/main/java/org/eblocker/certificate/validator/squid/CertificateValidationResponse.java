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

public class CertificateValidationResponse extends CertificateValidationMessage {

    public final String ERROR_REASON_DEFAULT = "Validated_by_eBlocker_TLS_validator";
    public static final String CERTIFICATE_VALIDATION_RESULT_OK = "OK";
    public static final String CERTIFICATE_VALIDATION_RESULT_FAILURE = "ERR";

    public static final String DEFAULT_ERROR_NAME = "X509_V_ERR_CERT_UNTRUSTED";

    private final boolean success;

    private final String[] errorReason;

    protected CertificateValidationResponse() {
        super(null, null, null, false);
        success = false;
        errorReason = null;
    }

    public CertificateValidationResponse(CertificateValidationRequest request, boolean useConcurrency, boolean success) {
        super(request.getId(), null, null, useConcurrency);
        this.success = success;
        if (!success) { //no error names specified, but it is not valid, so add default error name
            this.errorName = new String[]{ DEFAULT_ERROR_NAME };
        }
        this.errorReason = new String[]{ ERROR_REASON_DEFAULT };
    }

    public CertificateValidationResponse(CertificateValidationRequest request, boolean useConcurrency, String[] errorName) {
        super(request.getId(), errorName, null, useConcurrency);
        this.success = false;
        if (errorName == null) {//this constructor implies, that the responds is false, so we have to make sure to at least add our default error
            this.errorName = new String[]{ ERROR_REASON_DEFAULT };
        }
        this.errorReason = new String[]{ ERROR_REASON_DEFAULT };

    }

    public CertificateValidationResponse(Long id, String[] errorName, String[] errorCertId, boolean useConcurrency, boolean success, String[] errorReason) {
        super(id, errorName, errorCertId, useConcurrency);
        this.success = success;
        if (errorName == null) {
            this.errorName = new String[]{ ERROR_REASON_DEFAULT };
        }
        this.errorReason = errorReason == null ? new String[]{ ERROR_REASON_DEFAULT } : errorReason;
    }

    public boolean isSuccess() {
        return success;
    }

    public String[] getErrorReason() {
        return errorReason;
    }

    @Override
    protected String getMessage() {
        return success ? CERTIFICATE_VALIDATION_RESULT_OK : CERTIFICATE_VALIDATION_RESULT_FAILURE;
    }

    @Override
    protected String getContent() {
        StringBuilder s = new StringBuilder();

        if (!isSuccess()) {
            //add error_name_XY
            if (errorName.length > 0) {
                for (int i = 0; i < errorName.length; i++) {
                    String currErrorName = errorName[i];//.replace("SQUID_","");
                    s.append("error_name_")
                            .append(i)
                            .append("=")
                            .append(currErrorName)
                            .append("\n");
                }
            } else { // unsuccessful and no errors -> add default error
                s.append("error_name_0=")
                        .append(DEFAULT_ERROR_NAME)
                        .append("\n");
            }
            //add error_reason_XY
            for (int i = 0; i < errorReason.length; i++) {

                s.append("error_reason_")
                        .append(i)
                        .append("=")
                        .append(errorReason[i])
                        .append("\n");
            }

            //add error_cert_XY
        }

        return s.toString();
    }
}
