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
import org.eblocker.server.icap.filter.content.ContentFilterManager;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * REST controller for the {@link BlockerService}.
 */
public class BlockerControllerImpl implements BlockerController {

    private final BlockerService blockerService;
    private final ContentFilterManager contentFilterManager;

    @Inject
    public BlockerControllerImpl(BlockerService blockerService, ContentFilterManager contentFilterManager) {
        this.blockerService = blockerService;
        this.contentFilterManager = contentFilterManager;
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

    @Override
    public List<String> getElementHidingRules(Request request, Response response) {
        try {
            return contentFilterManager.getCustomRules();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read element hiding rules", e);
        }
    }

    @Override
    public List<String> updateElementHidingRules(Request request, Response response) {
        try {
            String[] rules = request.getBodyAs(String[].class);
            if (rules == null) {
                throw new BadRequestException("Missing element hiding rules");
            }
            return contentFilterManager.setCustomRules(Arrays.asList(rules));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException("Could not save element hiding rules", e);
        }
    }

    @Override
    public java.util.Map<String, Boolean> appendElementHidingRule(Request request, Response response) {
        java.util.Map<?, ?> body = request.getBodyAs(java.util.Map.class);
        Object rule = body == null ? null : body.get("rule");
        if (!(rule instanceof String)) throw new BadRequestException("Missing element hiding rule");
        try {
            boolean added = contentFilterManager.appendCustomRule((String) rule);
            return java.util.Map.of("added", added, "enabled", contentFilterManager.isCustomElementHidingEnabled());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException("Could not append element hiding rule", e);
        }
    }

    @Override
    public java.util.Map<String, Object> validateElementHidingRule(Request request, Response response) {
        java.util.Map<?, ?> body = request.getBodyAs(java.util.Map.class);
        Object rule = body == null ? null : body.get("rule");
        if (!(rule instanceof String)) throw new BadRequestException("Missing element hiding rule");
        try {
            String normalized = contentFilterManager.validateCustomRules(List.of((String) rule)).get(0);
            boolean duplicate = contentFilterManager.getCustomRules().contains(normalized);
            return java.util.Map.of("rule", normalized, "duplicate", duplicate);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException("Could not check element hiding rule", e);
        }
    }

    @Override
    public java.util.Map<String, Boolean> getElementHidingStatus(Request request, Response response) {
        return java.util.Map.of("enabled", contentFilterManager.isCustomElementHidingEnabled());
    }

    @Override
    public java.util.Map<String, Boolean> updateElementHidingStatus(Request request, Response response) {
        Boolean enabled = request.getBodyAs(Boolean.class);
        if (enabled == null) {
            throw new BadRequestException("Missing enabled state");
        }
        contentFilterManager.setCustomElementHidingEnabled(enabled);
        return java.util.Map.of("enabled", contentFilterManager.isCustomElementHidingEnabled());
    }

    private <T> Predicate<Blocker> createPredicate(String parameter, Function<String, T> paramFn, Function<Blocker, T> attributeFn) {
        if (parameter == null) {
            return b -> true;
        }
        T expected = paramFn.apply(parameter);
        return b -> expected.equals(attributeFn.apply(b));
    }
}
