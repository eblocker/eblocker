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
package org.eblocker.server.common.data.dashboard;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DashboardColumnsView {

    private static final Logger log = LoggerFactory.getLogger(DashboardColumnsView.class);

    private final List<UiCardColumnPosition> oneColumn;
    private final List<UiCardColumnPosition> twoColumn;
    private final List<UiCardColumnPosition> threeColumn;

    public DashboardColumnsView(@JsonProperty("oneColumn") List<UiCardColumnPosition> oneColumn,
                                @JsonProperty("twoColumn") List<UiCardColumnPosition> twoColumn,
                                @JsonProperty("threeColumn") List<UiCardColumnPosition> threeColumn) {
        this.oneColumn = oneColumn; // column 1
        this.twoColumn = twoColumn;
        this.threeColumn = threeColumn;
    }

    public DashboardColumnsView(Map<Integer, UiCardColumnPosition[]> idPositionMapping) {
        oneColumn = new ArrayList<>(); // column 1
        twoColumn = new ArrayList<>();
        threeColumn = new ArrayList<>();
        addUiCards(idPositionMapping);
    }

    public DashboardColumnsView() {
        oneColumn = new ArrayList<>(); // column 1
        twoColumn = new ArrayList<>();
        threeColumn = new ArrayList<>();
    }

    public void addUiCard(UiCardColumnPosition[] positions) {
        oneColumn.add(positions[0]);
        twoColumn.add(positions[1]);
        threeColumn.add(positions[2]);
    }

    public void addUiCards(Map<Integer, UiCardColumnPosition[]> idPositionMapping) {
        for (Entry<Integer, UiCardColumnPosition[]> entry : idPositionMapping.entrySet()) {
            addUiCard(entry.getValue());
        }
    }

    /**
     * All columns (one, two or three) MUST have the same cards. So just return oneColumn for a list of
     * all cards visible within this DashboardColumnsView object.
     *
     * @return
     */
    public List<UiCardColumnPosition> getOneColumn() {
        return oneColumn;
    }

    public List<UiCardColumnPosition> getTwoColumn() {
        return twoColumn;
    }

    public List<UiCardColumnPosition> getThreeColumn() {
        return threeColumn;
    }

    /**
     * All columns must contain the ID in order for contains(...) to return true. If only for one column the ID
     * is contained, this method returns false --> although this would be an inconsistent state. One-column-view,
     * two-column-view, three-column-view: all should contain all cards.
     *
     * @param id of the card
     * @return true if the card represented by the ID is contained in all three columns. Otherwise false.
     */
    public boolean contains(int id) {
        boolean columnOneHasId = false, columnTwoHasId = false, columnThreeHasId = false;

        for (UiCardColumnPosition visibility : oneColumn) {
            columnOneHasId = columnOneHasId || visibility.getId() == id;
        }

        for (UiCardColumnPosition visibility : twoColumn) {
            columnTwoHasId = columnTwoHasId || visibility.getId() == id;

        }

        for (UiCardColumnPosition visibility : threeColumn) {
            columnThreeHasId = columnThreeHasId || visibility.getId() == id;
        }

        if (!(columnOneHasId && columnTwoHasId && columnThreeHasId) && (columnOneHasId || columnTwoHasId || columnThreeHasId)) {
            log.error("Inconsistent state: {} / {} / {}", columnOneHasId, columnTwoHasId, columnThreeHasId);
        }

        return columnOneHasId && columnTwoHasId && columnThreeHasId;
    }

    public boolean isEmpty() {
        return oneColumn.isEmpty() && twoColumn.isEmpty() && threeColumn.isEmpty();
    }
}
