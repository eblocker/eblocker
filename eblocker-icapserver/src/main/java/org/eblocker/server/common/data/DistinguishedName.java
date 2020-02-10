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
package org.eblocker.server.common.data;


import org.apache.commons.lang3.StringUtils;

import javax.naming.ldap.Rdn;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class DistinguishedName {

    public static final String RDN_COUNTRY = "C";
    public static final String RDN_STATE = "S";
    public static final String RDN_LOCALITY = "L";
    public static final String RDN_ORGANISATION = "O";
    public static final String RDN_ORGANISATIONAL_UNIT = "OU";
    public static final String RDN_COMMON_NAME = "CN";

    private String commonName;
    private String organisation;
    private String organisationalUnit;
    private String locality;
    private String state;
    private String country;

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public String getOrganisation() {
        return organisation;
    }

    public void setOrganisation(String organisation) {
        this.organisation = organisation;
    }

    public String getOrganisationalUnit() {
        return organisationalUnit;
    }

    public void setOrganisationalUnit(String organisationalUnit) {
        this.organisationalUnit = organisationalUnit;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    /**
     * Generates a string representation suitable for openssl -subj option.
     */
    @Override
    public String toString() {
        StringBuilder dnBuilder = new StringBuilder();

        appendIfNotNull(dnBuilder, RDN_COUNTRY, getCountry());
        appendIfNotNull(dnBuilder, RDN_STATE, getState());
        appendIfNotNull(dnBuilder, RDN_LOCALITY, getLocality());
        appendIfNotNull(dnBuilder, RDN_ORGANISATION, getOrganisation());
        appendIfNotNull(dnBuilder, RDN_ORGANISATIONAL_UNIT, getOrganisationalUnit());
        appendIfNotNull(dnBuilder, RDN_COMMON_NAME, getCommonName());

        return dnBuilder.toString();
    }

    private StringBuilder appendIfNotNull(StringBuilder builder, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            value = Rdn.escapeValue(value); // escape according to rfc2253
            value = value.replaceAll("/", "\\\\/");  // escape all forward slashes as these are used to separate rdns on openssl's -subj option
            builder.append("/").append(key).append("=").append(value);
        }
        return builder;
    }

    /**
     * Creates a distinguished name parsing an RFC 2253 dn string
     */
    public static DistinguishedName fromRfc2253(String dnString) {
        Map<String, String> rdns = getRdns(dnString);

        DistinguishedName dn = new DistinguishedName();
        dn.setCommonName(rdns.get(RDN_COMMON_NAME));
        dn.setOrganisation(rdns.get(RDN_ORGANISATION));
        dn.setOrganisationalUnit(rdns.get(RDN_ORGANISATIONAL_UNIT));
        dn.setLocality(rdns.get(RDN_LOCALITY));
        dn.setState(rdns.get(RDN_STATE));
        dn.setCountry(rdns.get(RDN_COUNTRY));
        return dn;
    }

    /**
     * Extract relative distinguished names from a rfc 2253 dn
     * @param dn to parse
     * @return relative distinguished names with attribute type as key and attribute value as value
     */
    private static Map<String, String> getRdns(String dn) {
        return Arrays.asList(dn.split(",")).stream()
                .map(rdn-> rdn.split("="))
                .collect(Collectors.toMap(
                        rdn->rdn[0].trim(),
                        rdn->Rdn.unescapeValue(rdn[1].trim()).toString()));
    }
}
