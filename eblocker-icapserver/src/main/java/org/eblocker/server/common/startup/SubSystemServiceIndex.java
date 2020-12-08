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
package org.eblocker.server.common.startup;

import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.spi.ConstructorBinding;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedKeyBinding;
import org.eblocker.server.common.data.systemstatus.SubSystem;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Singleton
public class SubSystemServiceIndex {
    private Map<SubSystem, List<Class<?>>> servicesBySubSytem;

    public void scan(Map<Key<?>, Binding<?>> bindings) {
        servicesBySubSytem = bindings.values()
            .stream()
            .map(binding -> (Class<?>) binding.acceptTargetVisitor(new DefaultBindingTargetVisitor<Object, Class<?>>() {
                @Override
                public Class<?> visit(InstanceBinding<?> binding) {
                    return binding.getKey().getTypeLiteral().getRawType();
                }

                @Override
                public Class<?> visit(LinkedKeyBinding<?> binding) {
                    return binding.getLinkedKey().getTypeLiteral().getRawType();
                }

                @Override
                public Class<?> visit(ConstructorBinding<?> constructorBinding) {
                    return binding.getKey().getTypeLiteral().getRawType();
                }
            }))
            .filter(Objects::nonNull)
            .filter(c -> c.isAnnotationPresent(SubSystemService.class))
            // Sorting services for each subsystem by priority here relies on groupingBy preserving order of elements in
            // stream. This requires non-concurrent processing.
            .sorted(Comparator.comparingInt(c -> c.getAnnotation(SubSystemService.class).initPriority()))
            .collect(Collectors.groupingBy(c -> c.getAnnotation(SubSystemService.class).value()));
    }

    public Collection<Class<?>> getRegisteredServices(SubSystem subSystem) {
        Collection<Class<?>> services = servicesBySubSytem.get(subSystem);
        return services == null ? Collections.emptyList() : services;
    }
}
