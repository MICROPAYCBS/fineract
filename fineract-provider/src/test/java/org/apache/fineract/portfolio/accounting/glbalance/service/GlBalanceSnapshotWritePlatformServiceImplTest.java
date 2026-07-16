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
package org.apache.fineract.portfolio.accounting.glbalance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.database.RoutingDataSourceServiceFactory;
import org.apache.fineract.portfolio.accounting.glbalance.domain.GlBalanceSnapshotRepository;
import org.apache.fineract.portfolio.accounting.glbalance.domain.GlBalanceSnapshotTrackingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GlBalanceSnapshotWritePlatformServiceImplTest {

    @Mock
    private RoutingDataSourceServiceFactory dataSourceServiceFactory;
    @Mock
    private GlBalanceSnapshotRepository glBalanceSnapshotRepository;
    @Mock
    private GlBalanceSnapshotTrackingRepository glBalanceSnapshotTrackingRepository;
    @Mock
    private ConfigurationDomainService configurationDomainService;
    @Mock
    private FineractPlatformTenantConnection fineractPlatformTenantConnection;

    @InjectMocks
    private GlBalanceSnapshotWritePlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default Tenant", "default",
                fineractPlatformTenantConnection));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void updateSnapshotsIncrementally_ReturnsZeroWhenTargetDateBeforeFallback() {
        final LocalDate businessDate = LocalDate.of(2010, 1, 1);
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.COB_DATE, businessDate.minusDays(1),
                BusinessDateType.BUSINESS_DATE, businessDate)));

        final int rows = underTest.updateSnapshotsIncrementally(1L);

        assertEquals(0, rows);
    }

    @Test
    void backfillSnapshots_ReturnsZeroWhenTargetDateBeforeFallback() {
        final LocalDate businessDate = LocalDate.of(2010, 1, 1);
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.COB_DATE, businessDate.minusDays(1),
                BusinessDateType.BUSINESS_DATE, businessDate)));

        final int rows = underTest.backfillSnapshots(1L);

        assertEquals(0, rows);
    }
}
