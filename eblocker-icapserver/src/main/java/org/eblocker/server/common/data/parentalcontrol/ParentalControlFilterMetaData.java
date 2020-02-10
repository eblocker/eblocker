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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ParentalControlFilterMetaData {
    private Integer id;
    private Map<String, String> name;
    private Map<String, String> description;
    private List<String> filenames;
    private String version;
    private Date date;
    private String format;
    private Category category;
    private String filterType;
    private boolean builtin;
    private Integer size;
    private List<QueryTransformation> queryTransformations;
    private boolean disabled;
    private String customerCreatedName;
    private String customerCreatedDescription;

    public ParentalControlFilterMetaData() {
    }

    public ParentalControlFilterMetaData(
            Integer id,
            Map<String, String> name,
            Map<String, String> description,
            Category category,
            List<String> filenames,
            String version,
            Date date,
            String format,
            String filterType,
            boolean builtin,
            boolean disabled,
            List<QueryTransformation> queryTransformations,
            String customerCreatedName,
            String customerCreatedDescription) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.filenames = filenames;
        this.version = version;
        this.date = date;
        this.format = format;
        this.filterType = filterType;
        this.builtin = builtin;
        this.disabled = disabled;
        this.queryTransformations = queryTransformations;
        this.customerCreatedName = customerCreatedName;
        this.customerCreatedDescription = customerCreatedDescription;
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

    public void setName(Map<String, String> name) {
        this.name = name;
    }

    public Map<String, String> getDescription() {
        return description;
    }

    public void setDescription(Map<String, String> description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public List<String> getFilenames() {
        return filenames;
    }

    public void setFilenames(List<String> filenames) {
        this.filenames = filenames;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getFilterType() {
        return filterType;
    }

    public void setFilterType(String filterType) {
        this.filterType = filterType;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public void setBuiltin(boolean builtin) {
        this.builtin = builtin;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public List<QueryTransformation> getQueryTransformations() {
        return queryTransformations;
    }

    public void setQueryTransformations(List<QueryTransformation> queryTransformations) {
        this.queryTransformations = queryTransformations;
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

    public void setSize(Integer size) {
        this.size = size;
    }

    public Integer getSize() {
        return size;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ParentalControlFilterMetaData that = (ParentalControlFilterMetaData) o;

        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

}
