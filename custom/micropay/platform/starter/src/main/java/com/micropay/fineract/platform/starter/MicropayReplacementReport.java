/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.micropay.fineract.platform.starter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * Start-up self-check for the Micropay distribution.
 *
 * <p>
 * Every {@code Micropay*} service twin under {@code com.micropay.fineract} replaces an upstream implementation through
 * a {@code @ConditionalOnMissingBean} seam. If that seam stops matching (upstream renamed the implementation, a module
 * fell off the classpath, the server was started from {@code :fineract-provider} instead of
 * {@code :custom:micropay:app:boot}), the upstream implementation runs silently and Micropay behaviour disappears
 * without any error. This listener reports, once the context is ready, which Micropay auto-configurations and twins are
 * active and fails the start-up when a replaced interface is still served by an upstream bean or when no twin is
 * present at all.
 *
 * <p>
 * {@code micropay.replacements.fail-on-missing=false} downgrades the failure to a warning.
 */
@Slf4j
@Component
public class MicropayReplacementReport implements ApplicationListener<ApplicationReadyEvent> {

    static final String MICROPAY_PACKAGE = "com.micropay.fineract";
    static final String UPSTREAM_PACKAGE = "org.apache.fineract";
    static final String TWIN_PREFIX = "Micropay";

    private final boolean failOnMissing;

    public MicropayReplacementReport(@Value("${micropay.replacements.fail-on-missing:true}") final boolean failOnMissing) {
        this.failOnMissing = failOnMissing;
    }

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {
        final ConfigurableApplicationContext context = event.getApplicationContext();
        final Report report = inspect(context.getBeanFactory());
        report.log();
        if (!report.problems.isEmpty()) {
            final String message = "Micropay replacement check failed: " + String.join("; ", report.problems)
                    + ". Start the server with :custom:micropay:app:boot:devRun (not :fineract-provider:devRun) and check the"
                    + " @ConditionalOnMissingBean seams listed in custom/micropay/docs/fork-patch-set.md";
            if (failOnMissing) {
                throw new IllegalStateException(message);
            }
            log.warn(message);
        }
    }

    static Report inspect(final ConfigurableListableBeanFactory beanFactory) {
        final Report report = new Report();
        for (final String beanName : beanFactory.getBeanDefinitionNames()) {
            final Class<?> beanType = beanFactory.getType(beanName, false);
            if (beanType == null) {
                continue;
            }
            final Class<?> userType = ClassUtils.getUserClass(beanType);
            if (!userType.getName().startsWith(MICROPAY_PACKAGE)) {
                continue;
            }
            if (userType.getSimpleName().endsWith("AutoConfiguration")) {
                report.autoConfigurations.add(userType.getSimpleName());
                continue;
            }
            if (!userType.getSimpleName().startsWith(TWIN_PREFIX)) {
                continue;
            }
            for (final Class<?> iface : ClassUtils.getAllInterfacesForClassAsSet(userType)) {
                if (!iface.getName().startsWith(UPSTREAM_PACKAGE)) {
                    continue;
                }
                final String[] candidates = beanFactory.getBeanNamesForType(iface, true, false);
                final List<String> upstreamBeans = new ArrayList<>();
                for (final String candidate : candidates) {
                    final Class<?> candidateType = beanFactory.getType(candidate, false);
                    if (candidateType != null && !ClassUtils.getUserClass(candidateType).getName().startsWith(MICROPAY_PACKAGE)) {
                        upstreamBeans.add(ClassUtils.getUserClass(candidateType).getSimpleName());
                    }
                }
                report.twins.put(iface.getSimpleName(), userType.getSimpleName());
                if (!upstreamBeans.isEmpty()) {
                    report.problems.add(iface.getSimpleName() + " is still served by upstream bean(s) " + upstreamBeans + " next to "
                            + userType.getSimpleName());
                }
            }
        }
        if (report.twins.isEmpty()) {
            report.problems.add("no Micropay service twin found in the application context (custom/micropay modules missing)");
        }
        return report;
    }

    static final class Report {

        final Set<String> autoConfigurations = new LinkedHashSet<>();
        final Map<String, String> twins = new TreeMap<>();
        final List<String> problems = new ArrayList<>();

        void log() {
            log.info("MicropayReplacementReport: {} Micropay auto-configuration(s) active: {}", autoConfigurations.size(),
                    autoConfigurations);
            log.info("MicropayReplacementReport: {} upstream service interface(s) replaced by Micropay twins", twins.size());
            twins.forEach((iface, twin) -> log.info("MicropayReplacementReport:   {} -> {}", iface, twin));
            problems.forEach(problem -> log.error("MicropayReplacementReport: PROBLEM {}", problem));
        }
    }
}
