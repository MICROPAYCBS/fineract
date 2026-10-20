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
package org.apache.fineract.scoring.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * One version of an expert scorecard: base points, points per characteristic bin, grade bands and knock-out rules. A
 * scorecard is identified by its code; each change after activation creates a new version, so every decision points
 * at the exact version that produced it. The entity name avoids a clash with the survey module's {@code Scorecard}.
 */
@Getter
@Setter
@Entity(name = "ScoringScorecard")
@NoArgsConstructor
@Table(name = "m_scoring_scorecard")
public class Scorecard extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scorecard_type", nullable = false, length = 20)
    private ScorecardType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScorecardStatus status;

    @Column(name = "base_points", nullable = false, precision = 19, scale = 6)
    private BigDecimal basePoints;

    @Column(name = "activated_by")
    private Long activatedBy;

    @Column(name = "activated_on_utc")
    private OffsetDateTime activatedOn;

    @OneToMany(mappedBy = "scorecard", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ScorecardBin> bins = new ArrayList<>();

    @OneToMany(mappedBy = "scorecard", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("minScore")
    private List<ScorecardGradeBand> gradeBands = new ArrayList<>();

    @OneToMany(mappedBy = "scorecard", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ScorecardRule> rules = new ArrayList<>();

    public static Scorecard createDraft(final String code, final Integer version, final String name, final String description,
            final ScorecardType type, final BigDecimal basePoints) {
        final Scorecard scorecard = new Scorecard();
        scorecard.setCode(code);
        scorecard.setVersion(version);
        scorecard.setName(name);
        scorecard.setDescription(description);
        scorecard.setType(type);
        scorecard.setStatus(ScorecardStatus.DRAFT);
        scorecard.setBasePoints(basePoints);
        return scorecard;
    }

    public void addBin(final ScorecardBin bin) {
        bin.setScorecard(this);
        this.bins.add(bin);
    }

    public void addGradeBand(final ScorecardGradeBand band) {
        band.setScorecard(this);
        this.gradeBands.add(band);
    }

    public void addRule(final ScorecardRule rule) {
        rule.setScorecard(this);
        this.rules.add(rule);
    }

    public void clearStructure() {
        this.bins.clear();
        this.gradeBands.clear();
        this.rules.clear();
    }

    public boolean isDraft() {
        return this.status == ScorecardStatus.DRAFT;
    }

    public boolean isActive() {
        return this.status == ScorecardStatus.ACTIVE;
    }

    public void activate(final Long userId, final OffsetDateTime when) {
        this.status = ScorecardStatus.ACTIVE;
        this.activatedBy = userId;
        this.activatedOn = when;
    }

    public void retire() {
        this.status = ScorecardStatus.RETIRED;
    }
}
