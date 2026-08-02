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
package org.apache.fineract.portfolio.client.exception;

import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;

/**
 * Thrown when attempting to delete a client that still has associated child records.
 */
public class ClientCannotBeDeletedHasAssociatedDataException extends AbstractPlatformDomainRuleException {

    public enum AssociatedDataType {

        FAMILY_MEMBERS("error.msg.client.cannot.be.deleted.has.family.members",
                "Client with identifier %d cannot be deleted because it has one or more family members. "
                        + "Remove all family members before deleting the client.",
                "FK_m_family_members_client_id_m_client"),
        ADDRESSES("error.msg.client.cannot.be.deleted.has.addresses",
                "Client with identifier %d cannot be deleted because it has one or more addresses. "
                        + "Remove all addresses before deleting the client.",
                "clientaddressfk"),
        CONTACTS("error.msg.client.cannot.be.deleted.has.contacts",
                "Client with identifier %d cannot be deleted because it has one or more contacts. "
                        + "Remove all contacts before deleting the client.",
                "fk_client_contact_client"),
        INCOME_SOURCES("error.msg.client.cannot.be.deleted.has.income.sources",
                "Client with identifier %d cannot be deleted because it has one or more income sources. "
                        + "Remove all income sources before deleting the client.",
                "FK_m_client_income_source_client_id"),
        COMPLIANCE_PROFILE("error.msg.client.cannot.be.deleted.has.compliance.profile",
                "Client with identifier %d cannot be deleted because it has a compliance profile. "
                        + "Remove the compliance profile before deleting the client.",
                "FK_m_client_compliance_profile_client_id"),
        OTHER_BANK_ACCOUNTS("error.msg.client.cannot.be.deleted.has.other.bank.accounts",
                "Client with identifier %d cannot be deleted because it has one or more other bank accounts. "
                        + "Remove all other bank accounts before deleting the client.",
                "FK_m_client_other_bank_account_client_id"),
        IDENTIFIERS("error.msg.client.cannot.be.deleted.has.identifiers",
                "Client with identifier %d cannot be deleted because it has one or more identifiers. "
                        + "Remove all identifiers before deleting the client.",
                "FK_m_client_document_m_client");

        private final String errorCode;
        private final String messageTemplate;
        private final String foreignKeyConstraintName;

        AssociatedDataType(final String errorCode, final String messageTemplate, final String foreignKeyConstraintName) {
            this.errorCode = errorCode;
            this.messageTemplate = messageTemplate;
            this.foreignKeyConstraintName = foreignKeyConstraintName;
        }

        public String errorCode() {
            return errorCode;
        }

        public String message(final Long clientId) {
            return String.format(messageTemplate, clientId);
        }

        public String foreignKeyConstraintName() {
            return foreignKeyConstraintName;
        }

        public static AssociatedDataType fromForeignKeyConstraintName(final String message) {
            if (message == null) {
                return null;
            }
            for (final AssociatedDataType type : values()) {
                if (message.contains(type.foreignKeyConstraintName)) {
                    return type;
                }
            }
            return null;
        }
    }

    public ClientCannotBeDeletedHasAssociatedDataException(final Long clientId, final AssociatedDataType associatedDataType) {
        super(associatedDataType.errorCode(), associatedDataType.message(clientId), clientId);
    }
}
