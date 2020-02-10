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
package org.eblocker.server.common.data.systemstatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.eblocker.server.common.data.UpdatingStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SystemStatusDetails {

    private final Date date;

    private final ExecutionState executionState;

    private final List<String> warnings;

    private final Collection<SubSystemDetails> subSystemDetails;
    
    private final String projectVersion;
    private final UpdatingStatus updatingStatus;

    @JsonCreator
    public SystemStatusDetails(
            @JsonProperty("date") Date date,
            @JsonProperty("executionState") ExecutionState executionState,
            @JsonProperty("warnings") List<String> warnings,
            @JsonProperty("subSystemDetails") Collection<SubSystemDetails> subSystemDetails,
            @JsonProperty("projectVersion") String projectVersion,
            UpdatingStatus updatingStatus) {
        this.date = date;
        this.executionState = executionState;
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
        this.subSystemDetails = subSystemDetails;
        this.projectVersion = projectVersion;
        this.updatingStatus = updatingStatus;
    }

    public Date getDate() {
        return date;
    }

    public ExecutionState getExecutionState() {
        return executionState;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public Collection<SubSystemDetails> getSubSystemDetails() {
        return subSystemDetails;
    }

    public String getProjectVersion() {
        return projectVersion;
    }

    public UpdatingStatus getUpdatingStatus(){
        return this.updatingStatus;
    }

}
