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
import org.restexpress.exception.BadRequestException;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * REST controller for the {@link BlockerService}.
 */
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
    public Blocker createBlocker(Request request, Response response) {
        Blocker blocker = request.getBodyAs(Blocker.class);
        if (blocker == null) {
            throw new BadRequestException("Blocker payload is missing in request.");
        }
        return blockerService.createBlocker(blocker);
    }

    @Override
    public Blocker updateBlocker(Request request, Response response) {
        Blocker blocker = request.getBodyAs(Blocker.class);
        if (blocker == null) {
            throw new BadRequestException("Blocker payload is missing in request.");
        }
        return blockerService.updateBlocker(blocker);
    }

    @Override
    public void removeBlocker(Request request, Response response) {
        String id = request.getHeader("id");
        if (id == null) {
            throw new BadRequestException("Required parameter id is missing in request.");
        }
        int blockerId;
        try {
            blockerId = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid blocker id: " + id, e);
        }
        blockerService.deleteBlocker(blockerId);
    }

    private <T> Predicate<Blocker> createPredicate(String parameter, Function<String, T> paramFn, Function<Blocker, T> attributeFn) {
        if (parameter == null) {
            return b -> true;
        }
        T expected;
        try {
            expected = paramFn.apply(parameter);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid query filter parameter: " + parameter, e);
        }
        return b -> expected.equals(attributeFn.apply(b));
    }
}
