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
package com.micropay.fineract.platform.command;

import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_ACCRUE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_COPY;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_IMPORT;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_REPAIR;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_DEACTIVATE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_GL_TO_SAVINGS;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_MARKASNPA;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_OVERRIDE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_RELEASE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_RESETTOTP;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_RETIRE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_REVOKE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_SAVINGS_TO_GL;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_SCORE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_SUBMIT;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_SUBSCRIBE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_TREASURY_AUTOMATCH;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_TREASURY_EXPLAIN;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_TREASURY_RECEIVE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_TREASURY_REVERSE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_TREASURY_SEND;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_TREASURY_UNMATCH;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_UNDOMARKASNPA;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_UNSUBSCRIBE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ACTION_UPDATECHANNELLIMIT;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_CLIENTCONTACT;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_CLIENTTITLE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_COMPLIANCEPROFILE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_CONTACTTYPE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_CUSTOMERCLASS;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_DEPARTMENT;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_FRAUD_HOLD;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_IDENTITYTYPE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_INCOMESOURCES;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_INDUSTRY;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_INTERBRANCHRULE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_JOBSEQUENCE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_LEGAL_TENDER;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_SAVINGSACCOUNTPAYMENTCHANNEL;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_SCORECARD;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_SCORING_AUTHORITY_RULE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_SCORING_DECISION;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_SCORING_LIMIT_POLICY;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_SCORING_PRODUCT_CONFIG;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_SECTOR;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_SUBINDUSTRY;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_TRANSACTIONBATCH;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_TRANSACTIONBATCHITEM;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_TRANSACTIONBATCHSETTING;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_TREASURYACCOUNT;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_TREASURYAGENTFLOAT;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_TREASURYFLOATREQUEST;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_TREASURYRECONMATCH;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_TREASURYSETTING;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_TREASURYSTATEMENT;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_TREASURYSTATEMENTFORMAT;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_TREASURYSTATEMENTLINE;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_TREASURYTRANSFER;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_USERSESSION;
import static com.micropay.fineract.platform.command.MicropayCommandWrapperConstants.ENTITY_WORKFLOW_DEFINITION;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_ACTIVATE;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_APPROVE;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_BLOCK;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_CANCEL;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_CLOSE;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_CREATE;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_DELETE;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_EXECUTE;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_UNBLOCK;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_UPDATE;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ENTITY_CLIENT;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ENTITY_JOURNALENTRY;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ENTITY_LOAN;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ENTITY_SAVINGSACCOUNT;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ENTITY_USER;

import java.util.Set;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.infrastructure.core.domain.ExternalId;

/**
 * Builds {@link CommandWrapper}s for the commands introduced by Micropay. This is the Micropay counterpart of
 * {@link org.apache.fineract.commands.service.CommandWrapperBuilder}; keeping the Micropay commands here means the
 * upstream builder stays untouched and merges cleanly.
 */
@SuppressWarnings({ "HideUtilityClassConstructor", "unused" })
public class MicropayCommandWrapperBuilder {

    private Long groupId;
    private Long clientId;
    private Long loanId;
    private Long savingsId;
    private String actionName;
    private String entityName;
    private Long entityId;
    private Long subentityId;
    private String href;
    private String json = "{}";
    private String transactionId;
    private Long productId;
    private Long organisationCreditBureauId;
    private String jobName;
    private ExternalId loanExternalId;
    private Set<String> sanitizeJsonKeys;

    public CommandWrapper build() {
        return new CommandWrapper(null, this.groupId, this.clientId, this.loanId, this.savingsId, this.actionName, this.entityName,
                this.entityId, this.subentityId, this.href, this.json, this.transactionId, this.productId, null, null,
                this.organisationCreditBureauId, this.jobName, null, this.loanExternalId, this.sanitizeJsonKeys);
    }

    public CommandWrapper build(final String idempotencyKey) {
        return new CommandWrapper(null, this.groupId, this.clientId, this.loanId, this.savingsId, this.actionName, this.entityName,
                this.entityId, this.subentityId, this.href, this.json, this.transactionId, this.productId, null, null,
                this.organisationCreditBureauId, this.jobName, idempotencyKey, this.loanExternalId, this.sanitizeJsonKeys);
    }

    public MicropayCommandWrapperBuilder withLoanId(final Long withLoanId) {
        this.loanId = withLoanId;
        return this;
    }

    public MicropayCommandWrapperBuilder withSavingsId(final Long withSavingsId) {
        this.savingsId = withSavingsId;
        return this;
    }

    public MicropayCommandWrapperBuilder withClientId(final Long withClientId) {
        this.clientId = withClientId;
        return this;
    }

    public MicropayCommandWrapperBuilder withGroupId(final Long withGroupId) {
        this.groupId = withGroupId;
        return this;
    }

    public MicropayCommandWrapperBuilder withSubEntityId(final Long withSubEntityId) {
        this.subentityId = withSubEntityId;
        return this;
    }

    public MicropayCommandWrapperBuilder withJson(final String withJson) {
        this.json = withJson;
        return this;
    }

    public MicropayCommandWrapperBuilder withNoJsonBody() {
        this.json = null;
        return this;
    }

    public MicropayCommandWrapperBuilder addIncomeSources(final long clientId) {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_INCOMESOURCES;
        this.href = "/clients/" + clientId + "/incomesources";
        this.clientId = clientId;
        return this;
    }

    public MicropayCommandWrapperBuilder updateIncomeSources(final long incomeSourceId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_INCOMESOURCES;
        this.href = "/clients/" + clientId + "/incomesources";
        this.entityId = incomeSourceId;
        return this;
    }

    public MicropayCommandWrapperBuilder deleteIncomeSources(final long incomeSourceId) {
        this.actionName = ACTION_DELETE;
        this.entityName = ENTITY_INCOMESOURCES;
        this.href = "/clients/" + clientId + "/incomesources";
        this.entityId = incomeSourceId;
        return this;
    }

    public MicropayCommandWrapperBuilder updateComplianceProfile(final long clientId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_COMPLIANCEPROFILE;
        this.href = "/clients/" + clientId + "/complianceprofile";
        this.clientId = clientId;
        return this;
    }

    public MicropayCommandWrapperBuilder createCustomerClass() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_CUSTOMERCLASS;
        this.href = "/customerclasses";
        return this;
    }

    public MicropayCommandWrapperBuilder updateCustomerClass(final long customerClassId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_CUSTOMERCLASS;
        this.href = "/customerclasses/" + customerClassId;
        this.entityId = customerClassId;
        return this;
    }

    public MicropayCommandWrapperBuilder deleteCustomerClass(final long customerClassId) {
        this.actionName = ACTION_DELETE;
        this.entityName = ENTITY_CUSTOMERCLASS;
        this.href = "/customerclasses/" + customerClassId;
        this.entityId = customerClassId;
        return this;
    }

    public MicropayCommandWrapperBuilder createClientTitle() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_CLIENTTITLE;
        this.href = "/clienttitles";
        return this;
    }

    public MicropayCommandWrapperBuilder updateClientTitle(final long clientTitleId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_CLIENTTITLE;
        this.href = "/clienttitles/" + clientTitleId;
        this.entityId = clientTitleId;
        return this;
    }

    public MicropayCommandWrapperBuilder deleteClientTitle(final long clientTitleId) {
        this.actionName = ACTION_DELETE;
        this.entityName = ENTITY_CLIENTTITLE;
        this.href = "/clienttitles/" + clientTitleId;
        this.entityId = clientTitleId;
        return this;
    }

    public MicropayCommandWrapperBuilder createContactType() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_CONTACTTYPE;
        this.href = "/contacttypes";
        return this;
    }

    public MicropayCommandWrapperBuilder updateContactType(final long contactTypeId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_CONTACTTYPE;
        this.href = "/contacttypes/" + contactTypeId;
        this.entityId = contactTypeId;
        return this;
    }

    public MicropayCommandWrapperBuilder deleteContactType(final long contactTypeId) {
        this.actionName = ACTION_DELETE;
        this.entityName = ENTITY_CONTACTTYPE;
        this.href = "/contacttypes/" + contactTypeId;
        this.entityId = contactTypeId;
        return this;
    }

    public MicropayCommandWrapperBuilder createSector() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_SECTOR;
        this.href = "/sectors";
        return this;
    }

    public MicropayCommandWrapperBuilder updateSector(final long sectorId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_SECTOR;
        this.href = "/sectors/" + sectorId;
        this.entityId = sectorId;
        return this;
    }

    public MicropayCommandWrapperBuilder deleteSector(final long sectorId) {
        this.actionName = ACTION_DELETE;
        this.entityName = ENTITY_SECTOR;
        this.href = "/sectors/" + sectorId;
        this.entityId = sectorId;
        return this;
    }

    public MicropayCommandWrapperBuilder createDepartment() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_DEPARTMENT;
        this.href = "/departments";
        return this;
    }

    public MicropayCommandWrapperBuilder updateDepartment(final long departmentId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_DEPARTMENT;
        this.href = "/departments/" + departmentId;
        this.entityId = departmentId;
        return this;
    }

    public MicropayCommandWrapperBuilder deleteDepartment(final long departmentId) {
        this.actionName = ACTION_DELETE;
        this.entityName = ENTITY_DEPARTMENT;
        this.href = "/departments/" + departmentId;
        this.entityId = departmentId;
        return this;
    }

    public MicropayCommandWrapperBuilder createLegalTender(final String currencyCode) {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_LEGAL_TENDER;
        this.transactionId = currencyCode;
        this.href = "/currencies/" + currencyCode + "/legal-tenders";
        return this;
    }

    public MicropayCommandWrapperBuilder updateLegalTender(final String currencyCode, final Long legalTenderId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_LEGAL_TENDER;
        this.transactionId = currencyCode;
        this.entityId = legalTenderId;
        this.href = "/currencies/" + currencyCode + "/legal-tenders/" + legalTenderId;
        return this;
    }

    public MicropayCommandWrapperBuilder deleteLegalTender(final String currencyCode, final Long legalTenderId) {
        this.actionName = ACTION_DELETE;
        this.entityName = ENTITY_LEGAL_TENDER;
        this.transactionId = currencyCode;
        this.entityId = legalTenderId;
        this.href = "/currencies/" + currencyCode + "/legal-tenders/" + legalTenderId;
        return this;
    }

    public MicropayCommandWrapperBuilder createJobSequence() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_JOBSEQUENCE;
        this.href = "/jobsequences";
        return this;
    }

    public MicropayCommandWrapperBuilder updateJobSequence(final long sequenceId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_JOBSEQUENCE;
        this.href = "/jobsequences/" + sequenceId;
        this.entityId = sequenceId;
        return this;
    }

    public MicropayCommandWrapperBuilder deleteJobSequence(final long sequenceId) {
        this.actionName = ACTION_DELETE;
        this.entityName = ENTITY_JOBSEQUENCE;
        this.href = "/jobsequences/" + sequenceId;
        this.entityId = sequenceId;
        return this;
    }

    public MicropayCommandWrapperBuilder executeJobSequence(final long sequenceId) {
        this.actionName = ACTION_EXECUTE;
        this.entityName = ENTITY_JOBSEQUENCE;
        this.href = "/jobsequences/" + sequenceId + "?command=execute";
        this.entityId = sequenceId;
        return this;
    }

    public MicropayCommandWrapperBuilder cancelJobSequenceRun(final long sequenceId, final long runId) {
        this.actionName = ACTION_CANCEL;
        this.entityName = ENTITY_JOBSEQUENCE;
        this.href = "/jobsequences/" + sequenceId + "/runs/" + runId + "?command=cancel";
        this.entityId = sequenceId;
        this.subentityId = runId;
        return this;
    }

    public MicropayCommandWrapperBuilder createIndustry() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_INDUSTRY;
        this.href = "/industries";
        return this;
    }

    public MicropayCommandWrapperBuilder updateIndustry(final long industryId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_INDUSTRY;
        this.href = "/industries/" + industryId;
        this.entityId = industryId;
        return this;
    }

    public MicropayCommandWrapperBuilder deleteIndustry(final long industryId) {
        this.actionName = ACTION_DELETE;
        this.entityName = ENTITY_INDUSTRY;
        this.href = "/industries/" + industryId;
        this.entityId = industryId;
        return this;
    }

    public MicropayCommandWrapperBuilder createSubIndustry() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_SUBINDUSTRY;
        this.href = "/subindustries";
        return this;
    }

    public MicropayCommandWrapperBuilder updateSubIndustry(final long subIndustryId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_SUBINDUSTRY;
        this.href = "/subindustries/" + subIndustryId;
        this.entityId = subIndustryId;
        return this;
    }

    public MicropayCommandWrapperBuilder deleteSubIndustry(final long subIndustryId) {
        this.actionName = ACTION_DELETE;
        this.entityName = ENTITY_SUBINDUSTRY;
        this.href = "/subindustries/" + subIndustryId;
        this.entityId = subIndustryId;
        return this;
    }

    public MicropayCommandWrapperBuilder createIdentityType() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_IDENTITYTYPE;
        this.href = "/identitytypes";
        return this;
    }

    public MicropayCommandWrapperBuilder updateIdentityType(final long identityTypeId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_IDENTITYTYPE;
        this.href = "/identitytypes/" + identityTypeId;
        this.entityId = identityTypeId;
        return this;
    }

    public MicropayCommandWrapperBuilder deleteIdentityType(final long identityTypeId) {
        this.actionName = ACTION_DELETE;
        this.entityName = ENTITY_IDENTITYTYPE;
        this.href = "/identitytypes/" + identityTypeId;
        this.entityId = identityTypeId;
        return this;
    }

    public MicropayCommandWrapperBuilder createInterBranchGlRule() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_INTERBRANCHRULE;
        this.href = "/interbranch/rules";
        return this;
    }

    public MicropayCommandWrapperBuilder updateInterBranchGlRule(final long ruleId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_INTERBRANCHRULE;
        this.href = "/interbranch/rules/" + ruleId;
        this.entityId = ruleId;
        return this;
    }

    public MicropayCommandWrapperBuilder deleteInterBranchGlRule(final long ruleId) {
        this.actionName = ACTION_DELETE;
        this.entityName = ENTITY_INTERBRANCHRULE;
        this.href = "/interbranch/rules/" + ruleId;
        this.entityId = ruleId;
        return this;
    }

    public MicropayCommandWrapperBuilder createClientContact(final Long clientId) {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_CLIENTCONTACT;
        this.entityId = null;
        this.clientId = clientId;
        this.href = "/clients/" + clientId + "/contacts/template";
        return this;
    }

    public MicropayCommandWrapperBuilder updateClientContact(final Long clientId, final Long clientContactId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_CLIENTCONTACT;
        this.entityId = clientContactId;
        this.clientId = clientId;
        this.href = "/clients/" + clientId + "/contacts/" + clientContactId;
        return this;
    }

    public MicropayCommandWrapperBuilder deleteClientContact(final Long clientId, final Long clientContactId) {
        this.actionName = ACTION_DELETE;
        this.entityName = ENTITY_CLIENTCONTACT;
        this.entityId = clientContactId;
        this.clientId = clientId;
        this.href = "/clients/" + clientId + "/contacts/" + clientContactId;
        return this;
    }

    public MicropayCommandWrapperBuilder resetUserTotp(final Long userId) {
        this.actionName = ACTION_RESETTOTP;
        this.entityName = ENTITY_USER;
        this.entityId = userId;
        this.href = "/users/" + userId + "?command=resetTotp";
        this.json = "{}";
        return this;
    }

    public MicropayCommandWrapperBuilder submitClient(final Long clientId) {
        this.actionName = ACTION_SUBMIT;
        this.entityName = ENTITY_CLIENT;
        this.entityId = clientId;
        this.clientId = clientId;
        this.href = "/clients/" + clientId + "?command=submit";
        return this;
    }

    public MicropayCommandWrapperBuilder markAsNpa(final Long loanId) {
        this.actionName = ACTION_MARKASNPA;
        this.entityName = ENTITY_LOAN;
        this.entityId = loanId;
        this.loanId = loanId;
        this.href = "/loans/" + loanId + "?command=markAsNpa";
        return this;
    }

    public MicropayCommandWrapperBuilder undoMarkAsNpa(final Long loanId) {
        this.actionName = ACTION_UNDOMARKASNPA;
        this.entityName = ENTITY_LOAN;
        this.entityId = loanId;
        this.loanId = loanId;
        this.href = "/loans/" + loanId + "?command=undoMarkAsNpa";
        return this;
    }

    public MicropayCommandWrapperBuilder updateJournalEntryNarration(final String transactionId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_JOURNALENTRY;
        this.entityId = null;
        this.transactionId = transactionId;
        this.href = "/journalentries/" + transactionId;
        return this;
    }

    public MicropayCommandWrapperBuilder updateJournalEntryLineNarration(final Long journalEntryId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_JOURNALENTRY;
        this.entityId = journalEntryId;
        this.href = "/journalentries/entries/" + journalEntryId;
        return this;
    }

    public MicropayCommandWrapperBuilder updateJournalEntryLineNarrations(final String transactionId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_JOURNALENTRY;
        this.entityId = null;
        this.transactionId = transactionId;
        this.href = "/journalentries/" + transactionId;
        return this;
    }

    public MicropayCommandWrapperBuilder savingsAccountGlToSavings(final Long accountId) {
        this.actionName = ACTION_GL_TO_SAVINGS;
        this.entityName = ENTITY_SAVINGSACCOUNT;
        this.savingsId = accountId;
        this.entityId = null;
        this.href = "/savingsaccounts/" + accountId + "/transactions?command=glToSavings";
        return this;
    }

    public MicropayCommandWrapperBuilder savingsAccountSavingsToGl(final Long accountId) {
        this.actionName = ACTION_SAVINGS_TO_GL;
        this.entityName = ENTITY_SAVINGSACCOUNT;
        this.savingsId = accountId;
        this.entityId = null;
        this.href = "/savingsaccounts/" + accountId + "/transactions?command=savingsToGl";
        return this;
    }

    public MicropayCommandWrapperBuilder subscribeSavingsAccountPaymentChannel(final Long savingsAccountId) {
        this.actionName = ACTION_SUBSCRIBE;
        this.entityName = ENTITY_SAVINGSACCOUNTPAYMENTCHANNEL;
        this.savingsId = savingsAccountId;
        this.href = "/savingsaccounts/" + savingsAccountId + "/paymentchannels?command=subscribe";
        return this;
    }

    public MicropayCommandWrapperBuilder unsubscribeSavingsAccountPaymentChannel(final Long savingsAccountId) {
        this.actionName = ACTION_UNSUBSCRIBE;
        this.entityName = ENTITY_SAVINGSACCOUNTPAYMENTCHANNEL;
        this.savingsId = savingsAccountId;
        this.href = "/savingsaccounts/" + savingsAccountId + "/paymentchannels?command=unsubscribe";
        return this;
    }

    public MicropayCommandWrapperBuilder blockSavingsAccountPaymentChannel(final Long savingsAccountId) {
        this.actionName = ACTION_BLOCK;
        this.entityName = ENTITY_SAVINGSACCOUNTPAYMENTCHANNEL;
        this.savingsId = savingsAccountId;
        this.href = "/savingsaccounts/" + savingsAccountId + "/paymentchannels?command=block";
        return this;
    }

    public MicropayCommandWrapperBuilder unblockSavingsAccountPaymentChannel(final Long savingsAccountId) {
        this.actionName = ACTION_UNBLOCK;
        this.entityName = ENTITY_SAVINGSACCOUNTPAYMENTCHANNEL;
        this.savingsId = savingsAccountId;
        this.href = "/savingsaccounts/" + savingsAccountId + "/paymentchannels?command=unblock";
        return this;
    }

    public MicropayCommandWrapperBuilder updateSavingsAccountChannelLimit(final Long savingsAccountId) {
        this.actionName = ACTION_UPDATECHANNELLIMIT;
        this.entityName = ENTITY_SAVINGSACCOUNT;
        this.savingsId = savingsAccountId;
        this.href = "/savingsaccounts/" + savingsAccountId + "/paymentchannels?command=updateLimit";
        return this;
    }

    public MicropayCommandWrapperBuilder revokeUserSession(final Long userId, final Long sessionId) {
        this.actionName = ACTION_REVOKE;
        this.entityName = ENTITY_USERSESSION;
        this.entityId = userId;
        this.subentityId = sessionId;
        this.href = "/users/" + userId + "/sessions/" + sessionId + "/revoke";
        this.json = "{}";
        return this;
    }

    public MicropayCommandWrapperBuilder accrueLoan(final Long loanId) {
        this.actionName = ACTION_ACCRUE;
        this.entityName = ENTITY_LOAN;
        this.entityId = loanId;
        this.loanId = loanId;
        this.href = "/loans/" + loanId + "?command=accrue";
        return this;
    }

    public MicropayCommandWrapperBuilder createWorkflowDefinition() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_WORKFLOW_DEFINITION;
        this.href = "/workflow-definitions";
        return this;
    }

    public MicropayCommandWrapperBuilder updateWorkflowDefinition(final Long definitionId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_WORKFLOW_DEFINITION;
        this.entityId = definitionId;
        this.href = "/workflow-definitions/" + definitionId;
        return this;
    }

    public MicropayCommandWrapperBuilder deleteWorkflowDefinition(final Long definitionId) {
        this.actionName = ACTION_DELETE;
        this.entityName = ENTITY_WORKFLOW_DEFINITION;
        this.entityId = definitionId;
        this.href = "/workflow-definitions/" + definitionId;
        return this;
    }

    public MicropayCommandWrapperBuilder activateWorkflowDefinition(final Long definitionId) {
        this.actionName = ACTION_ACTIVATE;
        this.entityName = ENTITY_WORKFLOW_DEFINITION;
        this.entityId = definitionId;
        this.href = "/workflow-definitions/" + definitionId + "?command=activate";
        return this;
    }

    public MicropayCommandWrapperBuilder deactivateWorkflowDefinition(final Long definitionId) {
        this.actionName = ACTION_DEACTIVATE;
        this.entityName = ENTITY_WORKFLOW_DEFINITION;
        this.entityId = definitionId;
        this.href = "/workflow-definitions/" + definitionId + "?command=deactivate";
        return this;
    }

    public MicropayCommandWrapperBuilder createScorecard() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_SCORECARD;
        this.href = "/scoring/scorecards";
        return this;
    }

    public MicropayCommandWrapperBuilder updateScorecard(final Long scorecardId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_SCORECARD;
        this.entityId = scorecardId;
        this.href = "/scoring/scorecards/" + scorecardId;
        return this;
    }

    public MicropayCommandWrapperBuilder activateScorecard(final Long scorecardId) {
        this.actionName = ACTION_ACTIVATE;
        this.entityName = ENTITY_SCORECARD;
        this.entityId = scorecardId;
        this.href = "/scoring/scorecards/" + scorecardId + "?command=activate";
        return this;
    }

    public MicropayCommandWrapperBuilder retireScorecard(final Long scorecardId) {
        this.actionName = ACTION_RETIRE;
        this.entityName = ENTITY_SCORECARD;
        this.entityId = scorecardId;
        this.href = "/scoring/scorecards/" + scorecardId + "?command=retire";
        return this;
    }

    public MicropayCommandWrapperBuilder updateScoringProductConfig(final Long loanProductId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_SCORING_PRODUCT_CONFIG;
        this.entityId = loanProductId;
        this.productId = loanProductId;
        this.href = "/scoring/products/" + loanProductId;
        return this;
    }

    public MicropayCommandWrapperBuilder scoreLoan(final Long loanId) {
        this.actionName = ACTION_SCORE;
        this.entityName = ENTITY_LOAN;
        this.entityId = loanId;
        this.loanId = loanId;
        this.href = "/scoring/loans/" + loanId + "?command=score";
        return this;
    }

    public MicropayCommandWrapperBuilder overrideScoringDecision(final Long decisionId) {
        this.actionName = ACTION_OVERRIDE;
        this.entityName = ENTITY_SCORING_DECISION;
        this.entityId = decisionId;
        this.href = "/scoring/decisions/" + decisionId + "?command=override";
        return this;
    }

    public MicropayCommandWrapperBuilder createScoringAuthorityRule() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_SCORING_AUTHORITY_RULE;
        this.href = "/scoring/authority-rules";
        return this;
    }

    public MicropayCommandWrapperBuilder updateScoringAuthorityRule(final Long ruleId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_SCORING_AUTHORITY_RULE;
        this.entityId = ruleId;
        this.href = "/scoring/authority-rules/" + ruleId;
        return this;
    }

    public MicropayCommandWrapperBuilder deleteScoringAuthorityRule(final Long ruleId) {
        this.actionName = ACTION_DELETE;
        this.entityName = ENTITY_SCORING_AUTHORITY_RULE;
        this.entityId = ruleId;
        this.href = "/scoring/authority-rules/" + ruleId;
        return this;
    }

    public MicropayCommandWrapperBuilder createScoringLimitPolicy() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_SCORING_LIMIT_POLICY;
        this.href = "/scoring/limit-policies";
        return this;
    }

    public MicropayCommandWrapperBuilder updateScoringLimitPolicy(final Long policyId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_SCORING_LIMIT_POLICY;
        this.entityId = policyId;
        this.href = "/scoring/limit-policies/" + policyId;
        return this;
    }

    public MicropayCommandWrapperBuilder createTreasuryAccount() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_TREASURYACCOUNT;
        this.href = "/treasury/accounts";
        return this;
    }

    public MicropayCommandWrapperBuilder updateTreasuryAccount(final Long accountId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_TREASURYACCOUNT;
        this.entityId = accountId;
        this.href = "/treasury/accounts/" + accountId;
        return this;
    }

    public MicropayCommandWrapperBuilder closeTreasuryAccount(final Long accountId) {
        this.actionName = ACTION_CLOSE;
        this.entityName = ENTITY_TREASURYACCOUNT;
        this.entityId = accountId;
        this.href = "/treasury/accounts/" + accountId + "?command=close";
        return this;
    }

    public MicropayCommandWrapperBuilder createTreasuryTransfer() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_TREASURYTRANSFER;
        this.href = "/treasury/transfers";
        return this;
    }

    public MicropayCommandWrapperBuilder approveTreasuryTransfer(final Long transferId) {
        return treasuryTransferStep(transferId, ACTION_APPROVE, "approve");
    }

    public MicropayCommandWrapperBuilder sendTreasuryTransfer(final Long transferId) {
        return treasuryTransferStep(transferId, ACTION_TREASURY_SEND, "send");
    }

    public MicropayCommandWrapperBuilder receiveTreasuryTransfer(final Long transferId) {
        return treasuryTransferStep(transferId, ACTION_TREASURY_RECEIVE, "receive");
    }

    public MicropayCommandWrapperBuilder cancelTreasuryTransfer(final Long transferId) {
        return treasuryTransferStep(transferId, ACTION_CANCEL, "cancel");
    }

    public MicropayCommandWrapperBuilder reverseTreasuryTransfer(final Long transferId) {
        return treasuryTransferStep(transferId, ACTION_TREASURY_REVERSE, "reverse");
    }

    private MicropayCommandWrapperBuilder treasuryTransferStep(final Long transferId, final String action, final String command) {
        this.actionName = action;
        this.entityName = ENTITY_TREASURYTRANSFER;
        this.entityId = transferId;
        this.href = "/treasury/transfers/" + transferId + "?command=" + command;
        return this;
    }

    public MicropayCommandWrapperBuilder updateTreasurySettings() {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_TREASURYSETTING;
        this.href = "/treasury/settings";
        return this;
    }

    public MicropayCommandWrapperBuilder createTreasuryAgentFloat() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_TREASURYAGENTFLOAT;
        this.href = "/treasury/agent-floats";
        return this;
    }

    public MicropayCommandWrapperBuilder updateTreasuryAgentFloat(final Long agentFloatId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_TREASURYAGENTFLOAT;
        this.entityId = agentFloatId;
        this.href = "/treasury/agent-floats/" + agentFloatId;
        return this;
    }

    public MicropayCommandWrapperBuilder closeTreasuryAgentFloat(final Long agentFloatId) {
        this.actionName = ACTION_CLOSE;
        this.entityName = ENTITY_TREASURYAGENTFLOAT;
        this.entityId = agentFloatId;
        this.href = "/treasury/agent-floats/" + agentFloatId + "?command=close";
        return this;
    }

    /** The agent float travels as the sub-entity; the request gets its id when created. */
    public MicropayCommandWrapperBuilder createTreasuryFloatRequest(final Long agentFloatId) {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_TREASURYFLOATREQUEST;
        this.subentityId = agentFloatId;
        this.href = "/treasury/agent-floats/" + agentFloatId + "/requests";
        return this;
    }

    public MicropayCommandWrapperBuilder approveTreasuryFloatRequest(final Long requestId) {
        return treasuryFloatRequestStep(requestId, ACTION_APPROVE, "approve");
    }

    public MicropayCommandWrapperBuilder executeTreasuryFloatRequest(final Long requestId) {
        return treasuryFloatRequestStep(requestId, ACTION_EXECUTE, "execute");
    }

    public MicropayCommandWrapperBuilder cancelTreasuryFloatRequest(final Long requestId) {
        return treasuryFloatRequestStep(requestId, ACTION_CANCEL, "cancel");
    }

    public MicropayCommandWrapperBuilder reverseTreasuryFloatRequest(final Long requestId) {
        return treasuryFloatRequestStep(requestId, ACTION_TREASURY_REVERSE, "reverse");
    }

    private MicropayCommandWrapperBuilder treasuryFloatRequestStep(final Long requestId, final String action, final String command) {
        this.actionName = action;
        this.entityName = ENTITY_TREASURYFLOATREQUEST;
        this.entityId = requestId;
        this.href = "/treasury/float-requests/" + requestId + "?command=" + command;
        return this;
    }

    public MicropayCommandWrapperBuilder importTreasuryStatement() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_TREASURYSTATEMENT;
        this.href = "/treasury/statements";
        return this;
    }

    public MicropayCommandWrapperBuilder automatchTreasuryStatement(final Long statementId) {
        this.actionName = ACTION_TREASURY_AUTOMATCH;
        this.entityName = ENTITY_TREASURYSTATEMENT;
        this.entityId = statementId;
        this.href = "/treasury/statements/" + statementId + "?command=automatch";
        return this;
    }

    public MicropayCommandWrapperBuilder explainTreasuryStatementLine(final Long lineId) {
        this.actionName = ACTION_TREASURY_EXPLAIN;
        this.entityName = ENTITY_TREASURYSTATEMENTLINE;
        this.entityId = lineId;
        this.href = "/treasury/statements/lines/" + lineId + "?command=explain";
        return this;
    }

    public MicropayCommandWrapperBuilder createTreasuryReconMatch() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_TREASURYRECONMATCH;
        this.href = "/treasury/reconciliation/matches";
        return this;
    }

    public MicropayCommandWrapperBuilder unmatchTreasuryReconMatch(final Long matchId) {
        this.actionName = ACTION_TREASURY_UNMATCH;
        this.entityName = ENTITY_TREASURYRECONMATCH;
        this.entityId = matchId;
        this.href = "/treasury/reconciliation/matches/" + matchId;
        return this;
    }

    public MicropayCommandWrapperBuilder createTreasuryStatementFormat() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_TREASURYSTATEMENTFORMAT;
        this.href = "/treasury/statement-formats";
        return this;
    }

    public MicropayCommandWrapperBuilder updateTreasuryStatementFormat(final Long formatId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_TREASURYSTATEMENTFORMAT;
        this.entityId = formatId;
        this.href = "/treasury/statement-formats/" + formatId;
        return this;
    }

    /**
     * A treasury command on an entity (counterparty, deal type or deal). {@code entityId} is {@code null} for a create.
     */
    public MicropayCommandWrapperBuilder treasuryCommand(final String entity, final String action, final Long entityId, final String href) {
        this.actionName = action;
        this.entityName = entity;
        this.entityId = entityId;
        this.href = href;
        return this;
    }

    public MicropayCommandWrapperBuilder createFraudHold() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_FRAUD_HOLD;
        this.href = "/fraud/holds";
        return this;
    }

    public MicropayCommandWrapperBuilder releaseFraudHold(final Long holdId) {
        this.actionName = ACTION_RELEASE;
        this.entityName = ENTITY_FRAUD_HOLD;
        this.entityId = holdId;
        this.href = "/fraud/holds/" + holdId + "?command=release";
        return this;
    }

    public MicropayCommandWrapperBuilder createTransactionBatch() {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_TRANSACTIONBATCH;
        this.href = "/transactionbatches";
        return this;
    }

    public MicropayCommandWrapperBuilder updateTransactionBatch(final Long batchId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_TRANSACTIONBATCH;
        this.entityId = batchId;
        this.href = "/transactionbatches/" + batchId;
        return this;
    }

    public MicropayCommandWrapperBuilder deleteTransactionBatch(final Long batchId) {
        this.actionName = ACTION_DELETE;
        this.entityName = ENTITY_TRANSACTIONBATCH;
        this.entityId = batchId;
        this.href = "/transactionbatches/" + batchId;
        return this;
    }

    public MicropayCommandWrapperBuilder copyTransactionBatch(final Long batchId) {
        this.actionName = ACTION_COPY;
        this.entityName = ENTITY_TRANSACTIONBATCH;
        this.entityId = batchId;
        this.href = "/transactionbatches/" + batchId + "?command=copy";
        return this;
    }

    public MicropayCommandWrapperBuilder submitTransactionBatch(final Long batchId) {
        this.actionName = ACTION_SUBMIT;
        this.entityName = ENTITY_TRANSACTIONBATCH;
        this.entityId = batchId;
        this.href = "/transactionbatches/" + batchId + "?command=submit";
        return this;
    }

    public MicropayCommandWrapperBuilder createTransactionBatchItem(final Long batchId) {
        this.actionName = ACTION_CREATE;
        this.entityName = ENTITY_TRANSACTIONBATCHITEM;
        this.entityId = batchId;
        this.href = "/transactionbatches/" + batchId + "/items";
        return this;
    }

    public MicropayCommandWrapperBuilder updateTransactionBatchItem(final Long batchId, final Long itemId) {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_TRANSACTIONBATCHITEM;
        this.entityId = itemId;
        this.subentityId = batchId;
        this.href = "/transactionbatches/" + batchId + "/items/" + itemId;
        return this;
    }

    public MicropayCommandWrapperBuilder deleteTransactionBatchItem(final Long batchId, final Long itemId) {
        this.actionName = ACTION_DELETE;
        this.entityName = ENTITY_TRANSACTIONBATCHITEM;
        this.entityId = itemId;
        this.subentityId = batchId;
        this.href = "/transactionbatches/" + batchId + "/items/" + itemId;
        return this;
    }

    public MicropayCommandWrapperBuilder repairTransactionBatchItem(final Long batchId, final Long itemId, final Long postingId) {
        this.actionName = ACTION_REPAIR;
        this.entityName = ENTITY_TRANSACTIONBATCHITEM;
        this.entityId = itemId;
        this.subentityId = postingId;
        this.href = "/transactionbatches/" + batchId + "/items/" + itemId + "/postings/" + postingId + "?command=repair";
        return this;
    }

    public MicropayCommandWrapperBuilder updateTransactionBatchSettings() {
        this.actionName = ACTION_UPDATE;
        this.entityName = ENTITY_TRANSACTIONBATCHSETTING;
        this.href = "/transactionbatches/settings";
        return this;
    }
}
