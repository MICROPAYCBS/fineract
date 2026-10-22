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

/**
 * Command action and entity names introduced by Micropay. They complement
 * {@link org.apache.fineract.commands.domain.CommandWrapperConstants} and must match the {@code entity_name} /
 * {@code action_name} values used by the Micropay permissions and {@code @CommandType} handlers.
 */
public final class MicropayCommandWrapperConstants {

    private MicropayCommandWrapperConstants() {}

    public static final String ACTION_RESETTOTP = "RESETTOTP";
    public static final String ACTION_SUBMIT = "SUBMIT";
    public static final String ACTION_DEACTIVATE = "DEACTIVATE";
    public static final String ACTION_ACCRUE = "ACCRUE";
    public static final String ACTION_MARKASNPA = "MARKASNPA";
    public static final String ACTION_UNDOMARKASNPA = "UNDOMARKASNPA";
    public static final String ACTION_GL_TO_SAVINGS = "GLTOSAVINGS";
    public static final String ACTION_SAVINGS_TO_GL = "SAVINGSTOGL";
    public static final String ACTION_SUBSCRIBE = "SUBSCRIBE";
    public static final String ACTION_UPDATECHANNELLIMIT = "UPDATECHANNELLIMIT";
    public static final String ACTION_UNSUBSCRIBE = "UNSUBSCRIBE";
    public static final String ACTION_REVOKE = "REVOKE";
    public static final String ENTITY_INCOMESOURCES = "INCOMESOURCES";
    public static final String ENTITY_COMPLIANCEPROFILE = "COMPLIANCEPROFILE";
    public static final String ENTITY_CUSTOMERCLASS = "CUSTOMERCLASS";
    public static final String ENTITY_CLIENTTITLE = "CLIENTTITLE";
    public static final String ENTITY_CONTACTTYPE = "CONTACTTYPE";
    public static final String ENTITY_SECTOR = "SECTOR";
    public static final String ENTITY_DEPARTMENT = "DEPARTMENT";
    public static final String ENTITY_LEGAL_TENDER = "LEGAL_TENDER";
    public static final String ENTITY_JOBSEQUENCE = "JOBSEQUENCE";
    public static final String ENTITY_INDUSTRY = "INDUSTRY";
    public static final String ENTITY_SUBINDUSTRY = "SUBINDUSTRY";
    public static final String ENTITY_IDENTITYTYPE = "IDENTITYTYPE";
    public static final String ENTITY_INTERBRANCHRULE = "INTERBRANCHRULE";
    public static final String ENTITY_CLIENTCONTACT = "CLIENTCONTACT";
    public static final String ENTITY_SAVINGSACCOUNTPAYMENTCHANNEL = "SAVINGSACCOUNTPAYMENTCHANNEL";
    public static final String ENTITY_USERSESSION = "USERSESSION";
    public static final String ENTITY_WORKFLOW_DEFINITION = "WORKFLOW_DEFINITION";
    public static final String ENTITY_SCORECARD = "SCORECARD";
    public static final String ENTITY_SCORING_PRODUCT_CONFIG = "SCORING_PRODUCT_CONFIG";
    public static final String ACTION_RETIRE = "RETIRE";
    public static final String ACTION_SCORE = "SCORE";
    public static final String ENTITY_SCORING_DECISION = "SCORING_DECISION";
    public static final String ENTITY_SCORING_AUTHORITY_RULE = "SCORING_AUTHORITY_RULE";
    public static final String ACTION_OVERRIDE = "OVERRIDE";
    public static final String ENTITY_SCORING_LIMIT_POLICY = "SCORING_LIMIT_POLICY";
    public static final String ENTITY_TREASURYACCOUNT = "TREASURYACCOUNT";
    public static final String ENTITY_TREASURYTRANSFER = "TREASURYTRANSFER";
    public static final String ENTITY_TREASURYSETTING = "TREASURYSETTING";
    public static final String ENTITY_TREASURYAGENTFLOAT = "TREASURYAGENTFLOAT";
    public static final String ENTITY_TREASURYFLOATREQUEST = "TREASURYFLOATREQUEST";
    /** Upstream's {@code ACTION_SEND} is lower case; treasury permissions are SEND_TREASURYTRANSFER. */
    public static final String ACTION_TREASURY_SEND = "SEND";
    public static final String ACTION_TREASURY_RECEIVE = "RECEIVE";
    public static final String ACTION_TREASURY_REVERSE = "REVERSE";
    public static final String ENTITY_TREASURYSTATEMENT = "TREASURYSTATEMENT";
    public static final String ENTITY_TREASURYSTATEMENTFORMAT = "TREASURYSTATEMENTFORMAT";
    public static final String ENTITY_TREASURYSTATEMENTLINE = "TREASURYSTATEMENTLINE";
    public static final String ENTITY_TREASURYRECONMATCH = "TREASURYRECONMATCH";
    public static final String ACTION_TREASURY_AUTOMATCH = "AUTOMATCH";
    public static final String ACTION_TREASURY_UNMATCH = "UNMATCH";
    public static final String ACTION_TREASURY_EXPLAIN = "EXPLAIN";
}
