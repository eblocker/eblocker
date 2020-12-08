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
package org.eblocker.server.http.controller.impl;

import com.google.inject.Inject;
import org.eblocker.server.common.blocker.Blocker;
import org.eblocker.server.common.blocker.BlockerService;
import org.eblocker.server.common.blocker.BlockerType;
import org.eblocker.server.common.blocker.Category;
import org.eblocker.server.http.controller.BlockerController;
import org.restexpress.Request;
import org.restexpress.Response;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BlockerControllerImpl implements BlockerController {

    private final BlockerService blockerService;

    @Inject
    public BlockerControllerImpl(BlockerService blockerService) {
        this.blockerService = blockerService;
    }

    @Override
    public List<Blocker> getBlockers(Request request, Response response) {
        Predicate<Blocker> typePredicate = createPredicate(request.getHeader("type"), BlockerType::valueOf, Blocker::getType);
        Predicate<Blocker> categoryPredicate = createPredicate(request.getHeader("category"), Category::valueOf, Blocker::getCategory);
        Predicate<Blocker> filterTypePredicate = createPredicate(request.getHeader("filterType"), Function.identity(), Blocker::getFilterType);
        return blockerService.getBlockers().stream()
            .filter(typePredicate)
            .filter(categoryPredicate)
            .filter(filterTypePredicate)
            .collect(Collectors.toList());
    }

    @Override
    public Blocker getBlockerById(Request request, Response response) {
        return blockerService.getBlockerById(Integer.parseInt(request.getHeader("id")));
    }

    @Override
    public Blocker createBlocker(Request request, Response response) {
        Blocker blocker = request.getBodyAs(Blocker.class);
        return blockerService.createBlocker(blocker);
    }

    @Override
    public Blocker updateBlocker(Request request, Response response) {
        Blocker blocker = request.getBodyAs(Blocker.class);
        return blockerService.updateBlocker(blocker);
    }

    @Override
    public void removeBlocker(Request request, Response response) {
        blockerService.deleteBlocker(Integer.parseInt(request.getHeader("id")));
    }

    private <T> Predicate<Blocker> createPredicate(String parameter, Function<String, T> paramFn, Function<Blocker, T> attributeFn) {
        if (parameter == null) {
            return b -> true;
        }
        T expected = paramFn.apply(parameter);
        return b -> expected.equals(attributeFn.apply(b));
    }
}
