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
package org.eblocker.server.common.data.parentalcontrol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ParentalControlFilterSummaryData {
    private Integer id;
    private final Map<String, String> name;
    private final Map<String, String> description;
    private String customerCreatedName;
    private String customerCreatedDescription;
    private final String version;
    private Date lastUpdate;
    private final String filterType;// Black-/Whitelist
    private String format;// Domainlist/BPJM-Format
    private final Boolean builtin;
    private boolean disabled;
    private List<String> domains;
    private transient Supplier<Stream<String>> domainsStreamSupplier;
    private Category category;

    @JsonCreator
    @SuppressWarnings("unused")
    public ParentalControlFilterSummaryData(
            @JsonProperty("id") Integer id,
            @JsonProperty("name") Map<String, String> name,
            @JsonProperty("description") Map<String, String> description,
            @JsonProperty("version") String version,
            @JsonProperty("lastUpdate") Date lastUpdate,
            @JsonProperty("filterType") String filterType,
            @JsonProperty("builtin") boolean builtin,
            @JsonProperty("disabled") boolean disabled,
            @JsonProperty("domains") List<String> domains,
            @JsonProperty("customerCreatedName") String customerCreatedName,
            @JsonProperty("customerCreatedDescription") String customerCreatedDescription,
            @JsonProperty("category") Category category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.version = version;
        this.lastUpdate = lastUpdate;
        this.filterType = filterType;
        this.builtin = builtin;
        this.disabled = disabled;
        this.domains = domains;
        this.customerCreatedName = customerCreatedName;
        this.customerCreatedDescription = customerCreatedDescription;
        this.category = category;
    }

    public ParentalControlFilterSummaryData(ParentalControlFilterMetaData filterList) {
        this.id = filterList.getId();
        this.name = filterList.getName();
        this.description = filterList.getDescription();
        this.version = filterList.getVersion();
        this.lastUpdate = filterList.getDate();
        this.filterType = filterList.getFilterType();
        this.builtin = filterList.isBuiltin();
        this.disabled = filterList.isDisabled();
        this.customerCreatedName = filterList.getCustomerCreatedName();
        this.customerCreatedDescription = filterList.getCustomerCreatedDescription();
        this.category = filterList.getCategory();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Map<String, String> getName() {
        return name;
    }

    public Map<String, String> getDescription() {
        return description;
    }

    public String getCustomerCreatedName() {
        return customerCreatedName;
    }

    public void setCustomerCreatedName(String customerCreatedName) {
        this.customerCreatedName = customerCreatedName;
    }

    public String getCustomerCreatedDescription() {
        return customerCreatedDescription;
    }

    public void setCustomerCreatedDescription(String customerCreatedDescription) {
        this.customerCreatedDescription = customerCreatedDescription;
    }

    public String getVersion() {
        return version;
    }

    public Date getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Date lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public String getFilterType() {
        return filterType;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public List<String> getDomains() {
        return domains;
    }

    public Supplier<Stream<String>> getDomainsStreamSupplier() {
        return domainsStreamSupplier;
    }

    public void setDomainsStreamSupplier(
            Supplier<Stream<String>> domainsStreamSupplier) {
        this.domainsStreamSupplier = domainsStreamSupplier;
    }

    public Category getCategory() {
        return this.category;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ParentalControlFilterSummaryData that = (ParentalControlFilterSummaryData) o;

        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
