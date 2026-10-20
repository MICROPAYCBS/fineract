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
package org.apache.fineract.scoring.service;

import static org.apache.fineract.scoring.api.ScoringApiConstants.MODULE_ENABLED_PROPERTY;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.scoring.data.ScorecardRequest;
import org.apache.fineract.scoring.data.ScoringProductConfigRequest;
import org.apache.fineract.scoring.domain.RuleOperator;
import org.apache.fineract.scoring.domain.Scorecard;
import org.apache.fineract.scoring.domain.ScorecardBin;
import org.apache.fineract.scoring.domain.ScorecardGradeBand;
import org.apache.fineract.scoring.domain.ScorecardRepository;
import org.apache.fineract.scoring.domain.ScorecardRule;
import org.apache.fineract.scoring.domain.ScorecardStatus;
import org.apache.fineract.scoring.domain.ScorecardType;
import org.apache.fineract.scoring.domain.ScoringMode;
import org.apache.fineract.scoring.domain.ScoringOutcome;
import org.apache.fineract.scoring.domain.ScoringProductConfig;
import org.apache.fineract.scoring.domain.ScoringProductConfigRepository;
import org.apache.fineract.scoring.domain.ScoringRuleType;
import org.apache.fineract.scoring.exception.ScorecardNotFoundException;
import org.apache.fineract.scoring.exception.ScorecardStateException;
import org.apache.fineract.scoring.exception.ScoringConfigurationException;
import org.apache.fineract.scoring.serialization.ScorecardDataValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class ScorecardWritePlatformServiceImpl implements ScorecardWritePlatformService {

    private final ScorecardRepository scorecardRepository;
    private final ScoringProductConfigRepository productConfigRepository;
    private final ScorecardDataValidator validator;
    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates a DRAFT. When the code already exists the draft becomes the next version of that scorecard, which is how
     * an active scorecard is changed.
     */
    @Override
    public CommandProcessingResult create(final JsonCommand command) {
        final ScorecardRequest request = this.validator.validateAndParseScorecard(command.json());
        final int version = this.scorecardRepository.findMaxVersionByCode(request.getCode()) + 1;
        final Scorecard scorecard = Scorecard.createDraft(request.getCode(), version, request.getName(), request.getDescription(),
                ScorecardType.valueOf(request.getType()), request.getBasePoints());
        assembleStructure(scorecard, request);
        this.scorecardRepository.saveAndFlush(scorecard);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(scorecard.getId()).build();
    }

    @Override
    public CommandProcessingResult update(final Long scorecardId, final JsonCommand command) {
        final Scorecard scorecard = find(scorecardId);
        if (!scorecard.isDraft()) {
            throw new ScorecardStateException("updated", scorecardId, scorecard.getStatus().name());
        }
        final ScorecardRequest request = this.validator.validateAndParseScorecard(command.json());
        if (!scorecard.getCode().equals(request.getCode())) {
            throw new ScoringConfigurationException("code.immutable", "The code of a scorecard cannot change", scorecard.getCode());
        }
        scorecard.setName(request.getName());
        scorecard.setDescription(request.getDescription());
        scorecard.setType(ScorecardType.valueOf(request.getType()));
        scorecard.setBasePoints(request.getBasePoints());
        scorecard.clearStructure();
        this.scorecardRepository.flush();
        assembleStructure(scorecard, request);
        this.scorecardRepository.saveAndFlush(scorecard);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(scorecardId).build();
    }

    /**
     * Activation is the step maker-checker should govern: it puts a version in front of live applications. The
     * previously active version of the same code is retired in the same transaction.
     */
    @Override
    public CommandProcessingResult activate(final Long scorecardId) {
        final Scorecard scorecard = find(scorecardId);
        if (!scorecard.isDraft()) {
            throw new ScorecardStateException("activated", scorecardId, scorecard.getStatus().name());
        }
        validateForActivation(scorecard);
        this.scorecardRepository.findFirstByCodeAndStatus(scorecard.getCode(), ScorecardStatus.ACTIVE).ifPresent(previous -> {
            previous.retire();
            this.scorecardRepository.saveAndFlush(previous);
        });
        scorecard.activate(this.context.authenticatedUser().getId(), DateUtils.getAuditOffsetDateTime());
        this.scorecardRepository.saveAndFlush(scorecard);
        return new CommandProcessingResultBuilder().withEntityId(scorecardId).build();
    }

    @Override
    public CommandProcessingResult retire(final Long scorecardId) {
        final Scorecard scorecard = find(scorecardId);
        if (scorecard.getStatus() == ScorecardStatus.RETIRED) {
            throw new ScorecardStateException("retired", scorecardId, scorecard.getStatus().name());
        }
        scorecard.retire();
        this.scorecardRepository.saveAndFlush(scorecard);
        return new CommandProcessingResultBuilder().withEntityId(scorecardId).build();
    }

    @Override
    public CommandProcessingResult updateProductConfig(final Long loanProductId, final JsonCommand command) {
        final ScoringProductConfigRequest request = this.validator.validateAndParseProductConfig(command.json());
        final Long productCount = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM m_product_loan WHERE id = ?", Long.class,
                loanProductId);
        if (productCount == null || productCount == 0) {
            throw new ScoringConfigurationException("loan.product.not.found", "Loan product " + loanProductId + " does not exist",
                    loanProductId);
        }
        final ScoringMode mode = ScoringMode.valueOf(request.getMode());
        if (mode != ScoringMode.OFF && !this.scorecardRepository.existsByCode(request.getScorecardCode())) {
            throw new ScoringConfigurationException("scorecard.code.unknown", "No scorecard has code " + request.getScorecardCode(),
                    request.getScorecardCode());
        }
        final ScoringProductConfig config = this.productConfigRepository.findByLoanProductId(loanProductId)
                .orElseGet(() -> ScoringProductConfig.create(loanProductId, request.getScorecardCode(), mode));
        config.setScorecardCode(request.getScorecardCode());
        config.setMode(mode);
        this.productConfigRepository.saveAndFlush(config);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(loanProductId)
                .withProductId(loanProductId).build();
    }

    private Scorecard find(final Long scorecardId) {
        return this.scorecardRepository.findById(scorecardId).orElseThrow(() -> new ScorecardNotFoundException(scorecardId));
    }

    private static void assembleStructure(final Scorecard scorecard, final ScorecardRequest request) {
        nullToEmpty(request.getBins()).forEach(bin -> scorecard.addBin(ScorecardBin.create(bin.getCharacteristic(), bin.getLower(),
                bin.getUpper(), bin.getMatchValue(), Boolean.TRUE.equals(bin.getMissing()), bin.getPoints(), bin.getReasonCode())));
        nullToEmpty(request.getGradeBands()).forEach(band -> scorecard.addGradeBand(ScorecardGradeBand.create(band.getGrade(),
                band.getMinScore(), band.getMaxScore(), band.getExpectedPd(), ScoringOutcome.valueOf(band.getOutcome()))));
        nullToEmpty(request.getRules())
                .forEach(rule -> scorecard.addRule(ScorecardRule.create(ScoringRuleType.valueOf(rule.getRuleType()),
                        rule.getCharacteristic(), rule.getOperator() == null ? null : RuleOperator.valueOf(rule.getOperator()),
                        rule.getValue(), ScoringOutcome.valueOf(rule.getOutcome()), rule.getReasonCode())));
    }

    /**
     * A scorecard can go live only when every possible score maps to exactly one grade: at least one band, and no two
     * bands overlap.
     */
    static void validateForActivation(final Scorecard scorecard) {
        final List<ScorecardGradeBand> bands = new ArrayList<>(scorecard.getGradeBands());
        if (bands.isEmpty()) {
            throw new ScoringConfigurationException("grade.bands.required", "A scorecard needs at least one grade band to be activated",
                    scorecard.getCode());
        }
        bands.sort(Comparator.comparing(ScorecardGradeBand::getMinScore));
        for (int i = 1; i < bands.size(); i++) {
            final BigDecimal previousMax = bands.get(i - 1).getMaxScore();
            if (bands.get(i).getMinScore().compareTo(previousMax) <= 0) {
                throw new ScoringConfigurationException("grade.bands.overlap",
                        "Grade bands " + bands.get(i - 1).getGrade() + " and " + bands.get(i).getGrade() + " overlap",
                        bands.get(i - 1).getGrade(), bands.get(i).getGrade());
            }
        }
    }

    private static <T> List<T> nullToEmpty(final List<T> list) {
        return list == null ? List.of() : list;
    }
}
