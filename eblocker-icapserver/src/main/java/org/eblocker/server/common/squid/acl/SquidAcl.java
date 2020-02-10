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
package org.eblocker.server.common.squid.acl;

import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;

public abstract class SquidAcl {

    private static final Logger log = LoggerFactory.getLogger(SquidAcl.class);

    private final SimpleResource aclFile;
    private Set<String> aclEntries;

    SquidAcl(String path) {
        aclFile = new SimpleResource(path);
        if (!ResourceHandler.exists(aclFile)) {
            log.info("Creating temporary squid acl file {}.", path);
            ResourceHandler.create(aclFile);
        }
    }

    public synchronized boolean update() {
        Set<String> entries = getAclEntries();
        if (Objects.equals(aclEntries, entries)) {
            return false;
        }

        aclEntries = entries;
        ResourceHandler.replaceContent(aclFile, aclEntries);
        return true;
    }

    protected abstract Set<String> getAclEntries();
}
