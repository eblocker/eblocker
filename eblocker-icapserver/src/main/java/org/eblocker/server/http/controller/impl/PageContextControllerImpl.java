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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.eblocker.server.common.page.PageContext;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.http.controller.PageContextController;
import org.restexpress.Request;
import org.restexpress.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class PageContextControllerImpl implements PageContextController {

    private final PageContextStore pageContextStore;

    @Inject
    public PageContextControllerImpl(PageContextStore pageContextStore) {
        this.pageContextStore = pageContextStore;
    }

    @Override
    public void reportTopContent(Request request, Response response) {
        String id = request.getHeader("id");
        PageContext context = pageContextStore.get(id);
        if (context != null) {
            context.setParentContext(null);
        }
        response.addHeader("Access-Control-Allow-Origin", "*");
    }

    public List<Node> getPageContextTree(Request request, Response response) {
        Map<PageContext, Node> nodes = pageContextStore.getContexts().stream()
            .map(Node::new)
            .collect(Collectors.toMap(Node::getContext, Function.identity()));

        List<Node> rootNodes = new ArrayList<>();
        nodes.values().forEach(node -> {
            PageContext parent = node.context.getParentContext();
            if (parent == null) {
                rootNodes.add(node);
            } else {
                nodes.get(parent).childs.add(node);
            }
        });

        return rootNodes;
    }

    public static class Node {
        @JsonIgnore
        private PageContext context;

        private String id;
        private String url;

        private List<Node> childs = new ArrayList<>();

        public Node(PageContext context) {
            this.context = context;
            this.id = context.getId();
            this.url = context.getUrl();
        }

        @JsonIgnore
        public PageContext getContext() {
            return context;
        }

        public String getId() {
            return id;
        }

        public String getUrl() {
            return url;
        }

        public List<Node> getChilds() {
            return childs;
        }
    }
}
