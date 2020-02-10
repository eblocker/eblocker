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
package org.eblocker.server.common.data.migrations;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.dashboard.DashboardColumnsView;
import org.eblocker.server.common.data.dashboard.UiCard;
import org.eblocker.server.common.data.dashboard.UiCardColumnPosition;
import org.eblocker.server.http.service.DashboardCardService;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class SchemaMigrationVersion42 implements SchemaMigration {

    private final DataSource dataSource;
    private final DashboardCardService dashboardCardService;

    @Inject
    public SchemaMigrationVersion42(DataSource dataSource,
                                    DashboardCardService dashboardCardService) {
        this.dataSource = dataSource;
        this.dashboardCardService = dashboardCardService;
    }

    @Override
    public String getSourceVersion() {
        return "41";
    }

    @Override
    public String getTargetVersion() {
        return "42";
    }

    @Override
    public void migrate() {
        updateExpanseCollapseStatusOfAllUserCards();

        dataSource.setVersion("42");
    }

    private void updateExpanseCollapseStatusOfAllUserCards() {
        List<UserModule> users = dataSource.getAll(UserModule.class);
        users.forEach(user -> {
            DashboardColumnsView dcv = user.getDashboardColumnsView();
            List<UiCard> cards = dashboardCardService.getAll();

            List<UiCardColumnPosition> newPositionsOneColumn = new ArrayList<>();
            List<UiCardColumnPosition> newPositionsTwoColumn = new ArrayList<>();
            List<UiCardColumnPosition> newPositionsThreeColumn = new ArrayList<>();

            /*
             * Makes sure that the card can be found by dashboardCardService.getById and
             * also filters all cards that do not exist.
             */
            Predicate<UiCardColumnPosition> predicate = pos -> dashboardCardService.getById(cards, pos.getId()) != null;

            dcv.getOneColumn().stream().filter(predicate).forEach(pos -> {
                UiCard card = dashboardCardService.getById(cards, pos.getId());
                boolean expanded = card.getName().equals("CONNECTION_TEST");
                newPositionsOneColumn.add(new UiCardColumnPosition(pos.getId(), pos.getColumn(), pos.getIndex(), pos.isVisible(), expanded));
            });

            dcv.getTwoColumn().stream().filter(predicate).forEach(pos -> {
                newPositionsTwoColumn.add(new UiCardColumnPosition(pos.getId(), pos.getColumn(), pos.getIndex(), pos.isVisible(), true));
            });

            dcv.getThreeColumn().stream().filter(predicate).forEach(pos -> {
                newPositionsThreeColumn.add(new UiCardColumnPosition(pos.getId(), pos.getColumn(), pos.getIndex(), pos.isVisible(), true));
            });

            user.setDashboardColumnsView(new DashboardColumnsView(newPositionsOneColumn, newPositionsTwoColumn, newPositionsThreeColumn));
            dataSource.save(user, user.getId());
        });
    }

}
