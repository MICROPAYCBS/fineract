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
package org.apache.fineract.portfolio.client.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.apache.fineract.portfolio.client.data.ClientAddressRequest;

/**
 * Created by Chirag Gupta on 01/13/18.
 */
final class ClientsApiResourceSwagger {

    private ClientsApiResourceSwagger() {}

    @Schema(description = "GetClientsTemplateResponse")
    public static final class GetClientsTemplateResponse {

        private GetClientsTemplateResponse() {}

        static final class GetClientsOfficeOptions {

            private GetClientsOfficeOptions() {}

            @Schema(example = "1")
            public Long id;
            @Schema(example = "Head Office")
            public String name;
            @Schema(example = "Head Office")
            public String nameDecorated;
        }

        static final class GetClientsStaffOptions {

            private GetClientsStaffOptions() {}

            @Schema(example = "1")
            public Long id;
            @Schema(example = "xyz")
            public String firstname;
            @Schema(example = "sjs")
            public String lastname;
            @Schema(example = "sjs, xyz")
            public String displayName;
            @Schema(example = "1")
            public Long officeId;
            @Schema(example = "Head Office")
            public String officeName;
            @Schema(example = "true")
            public Boolean isLoanOfficer;
            @Schema(example = "true")
            public Boolean isActive;
        }

        static final class GetClientsSavingProductOptions {

            private GetClientsSavingProductOptions() {}

            @Schema(example = "4")
            public Long id;
            @Schema(example = "account overdraft")
            public String name;
            @Schema(example = "false")
            public Boolean withdrawalFeeForTransfers;
            @Schema(example = "false")
            public Boolean allowOverdraft;
        }

        static final class GetClientsDataTables {

            private GetClientsDataTables() {}

            static final class GetClientsColumnHeaderData {

                private GetClientsColumnHeaderData() {}

                @Schema(example = "client_id")
                public String columnName;
                @Schema(example = "bigint")
                public String columnType;
                @Schema(example = "0")
                public Integer columnLength;
                @Schema(example = "INTEGER")
                public String columnDisplayType;
                @Schema(example = "false")
                public Boolean isColumnNullable;
                @Schema(example = "true")
                public Boolean isColumnPrimaryKey;
                @Schema(example = "[]")
                public List<String> columnValues;
            }

            @Schema(example = "m_client")
            public String applicationTableName;
            @Schema(example = "Address Details")
            public String registeredTableName;
            public Set<GetClientsColumnHeaderData> columnHeaderData;
        }

        @Schema(example = "[2014, 3, 4]")
        public LocalDate activationDate;
        @Schema(example = "1")
        public Long officeId;
        public Set<GetClientsOfficeOptions> officeOptions;
        public Set<GetClientsStaffOptions> staffOptions;
        public Set<GetClientsSavingProductOptions> savingProductOptions;
        public Set<GetClientsDataTables> datatables;
        @Schema(description = "Titles from m_client_title")
        public Set<GetClientsClientIdResponse.GetClientsCodeValue> titleOptions;
        @Schema(description = "Nationality code values")
        public Set<GetClientsClientIdResponse.GetClientsCodeValue> nationalityOptions;
        @Schema(description = "Customer risk profile code values")
        public Set<GetClientsClientIdResponse.GetClientsCodeValue> customerRiskProfileOptions;
        @Schema(description = "Marital status code values")
        public Set<GetClientsClientIdResponse.GetClientsCodeValue> maritalStatusOptions;
        @Schema(description = "Customer classes that can be assigned")
        public Set<GetClientsClientIdResponse.GetClientsCustomerClass> customerClassOptions;
        @Schema(description = "Contact types that can be used on contacts")
        public Set<GetClientsClientIdResponse.GetClientsCodeValue> contactTypeOptions;
    }

    @Schema(description = "GetClientsResponse")
    public static final class GetClientsResponse {

        private GetClientsResponse() {}

        static final class GetClientsPageItemsResponse {

            private GetClientsPageItemsResponse() {}

            static final class GetClientStatus {

                private GetClientStatus() {}

                @Schema(example = "50")
                public Long id;
                @Schema(example = "clientStatusType.draft")
                public String code;
                @Schema(example = "Draft")
                public String description;
            }

            @Schema(example = "2")
            public Long id;
            @Schema(example = "000000002")
            public String accountNo;
            public GetClientStatus status;
            @Schema(example = "false")
            public Boolean active;
            @Schema(example = "Home Farm Produce")
            public String fullname;
            @Schema(example = "Home Farm Produce")
            public String displayName;
            @Schema(example = "1")
            public Long officeId;
            @Schema(example = "Head Office")
            public String officeName;
            @Schema(example = "test@test.com")
            public String emailAddress;
            @Schema(description = "Tax identification number", example = "TIN-100200")
            public String taxIdentificationNumber;
            @Schema(description = "Alternate mobile number", example = "+353851111111")
            public String alternativeMobileNo;
            @Schema(description = "Alternate email address", example = "alt@test.com")
            public String alternativeEmailAddress;
            @Schema(description = "Sub-industry id", example = "4")
            public Long subIndustryId;
            @Schema(description = "Customer class id", example = "2")
            public Long customerClassId;
            @Schema(description = "Customer class summary when a class is assigned")
            public GetClientsClientIdResponse.GetClientsCustomerClass customerClass;
            @Schema(description = "Title, from m_client_title")
            public GetClientsClientIdResponse.GetClientsCodeValue title;
            @Schema(description = "Nationality code value")
            public GetClientsClientIdResponse.GetClientsCodeValue nationality;
            @Schema(description = "Customer risk profile code value")
            public GetClientsClientIdResponse.GetClientsCodeValue customerRiskProfile;
            @Schema(description = "Marital status code value")
            public GetClientsClientIdResponse.GetClientsCodeValue maritalStatus;
        }

        @Schema(example = "2")
        public Integer totalFilteredRecords;
        public List<GetClientsPageItemsResponse> pageItems;
    }

    @Schema(description = "GetClientsClientIdResponse")
    public static final class GetClientsClientIdResponse {

        private GetClientsClientIdResponse() {}

        static final class GetClientsClientIdStatus {

            private GetClientsClientIdStatus() {}

            @Schema(example = "300")
            public Long id;
            @Schema(example = "clientStatusType.active")
            public String code;
            @Schema(example = "Active")
            public String description;
        }

        static final class GetClientsTimeline {

            private GetClientsTimeline() {}

            @Schema(example = "[2013, 1, 1]")
            public LocalDate submittedOnDate;
            @Schema(example = "mifos")
            public String submittedByUsername;
            @Schema(example = "App")
            public String submittedByFirstname;
            @Schema(example = "Administrator")
            public String submittedByLastname;
            @Schema(example = "[2013, 1, 1]")
            public LocalDate activatedOnDate;
            @Schema(example = "mifos")
            public String activatedByUsername;
            @Schema(example = "App")
            public String activatedByFirstname;
            @Schema(example = "Administrator")
            public String activatedByLastname;
            @Schema(description = "Closure date as [year, month, day]", example = "[2013, 1, 1]", type = "array")
            public List<Integer> closedOnDate;
            @Schema(example = "mifos")
            public String closedByUsername;
            @Schema(example = "App")
            public String closedByFirstname;
            @Schema(example = "Administrator")
            public String closedByLastname;

        }

        static final class GetClientsCodeValue {

            private GetClientsCodeValue() {}

            @Schema(example = "12")
            public Long id;
            @Schema(example = "Low")
            public String name;
        }

        static final class GetClientsCustomerClass {

            private GetClientsCustomerClass() {}

            @Schema(example = "2")
            public Long id;
            @Schema(example = "RETAIL")
            public String classCode;
            @Schema(example = "Retail individual")
            public String className;
        }

        static final class GetClientsLegalForm {

            private GetClientsLegalForm() {}

            @Schema(example = "1")
            public Long id;
            @Schema(example = "legalFormType.person")
            public String code;
            @Schema(example = "Person")
            public String value;
        }

        static final class GetClientsNonPerson {

            private GetClientsNonPerson() {}

            public GetClientsCodeValue constitution;
            @Schema(example = "INC-100")
            public String incorpNumber;
            @Schema(description = "Incorporation validity date as [year, month, day]", example = "[2030, 12, 31]", type = "array")
            public List<Integer> incorpValidityTillDate;
            public GetClientsCodeValue mainBusinessLine;
            @Schema(example = "Trading company")
            public String remarks;
        }

        static final class GetClientsGroups {

            private GetClientsGroups() {}

            @Schema(example = "000000001")
            public Long id;
            @Schema(example = "000000002")
            public String accountNo;
            @Schema(example = "Group name")
            public String name;
            @Schema(example = "000000003")
            public Long externalId;
        }

        @Schema(example = "27")
        public Long id;
        @Schema(example = "000000027")
        public String accountNo;
        public GetClientsClientIdStatus status;
        @Schema(example = "true")
        public Boolean active;
        @Schema(example = "[2013, 1, 1]")
        public LocalDate activationDate;
        @Schema(example = "savings")
        public String firstname;
        @Schema(example = "test")
        public String lastname;
        @Schema(example = "savings test")
        public String displayName;
        @Schema(example = "1")
        public Long officeId;
        @Schema(example = "Head Office")
        public String officeName;
        public GetClientsTimeline timeline;
        @Schema(example = "4")
        public Long savingsProductId;
        @Schema(example = "account overdraft")
        public String savingsProductName;
        @Schema(example = "[]")
        public List<GetClientsGroups> groups;
        @Schema(example = "test@test.com")
        public String emailAddress;
        @Schema(example = "123")
        public String externalId;
        @Schema(example = "Client")
        public String middlename;
        @Schema(example = "Client Full Name")
        public String fullname;
        @Schema(example = "+353851239876")
        public String mobileNo;
        @Schema(description = "Tax identification number", example = "TIN-100200")
        public String taxIdentificationNumber;
        @Schema(description = "Alternate mobile number", example = "+353851111111")
        public String alternativeMobileNo;
        @Schema(description = "Alternate email address", example = "alt@test.com")
        public String alternativeEmailAddress;
        @Schema(description = "Sub-industry id", example = "4")
        public Long subIndustryId;
        @Schema(description = "Customer class id", example = "2")
        public Long customerClassId;
        @Schema(description = "Customer class summary (id, classCode, className) when a class is assigned")
        public GetClientsCustomerClass customerClass;
        @Schema(description = "Title selected from m_client_title")
        public GetClientsCodeValue title;
        @Schema(description = "Nationality code value")
        public GetClientsCodeValue nationality;
        @Schema(description = "Customer risk profile code value")
        public GetClientsCodeValue customerRiskProfile;
        @Schema(description = "Marital status code value")
        public GetClientsCodeValue maritalStatus;
        @Schema(description = "Date of birth as [year, month, day]", example = "[1990, 4, 3]", type = "array")
        public List<Integer> dateOfBirth;
        @Schema(description = "Gender code value")
        public GetClientsCodeValue gender;
        @Schema(description = "Client type code value")
        public GetClientsCodeValue clientType;
        @Schema(description = "Client classification code value")
        public GetClientsCodeValue clientClassification;
        @Schema(example = "false")
        public Boolean isStaff;
        @Schema(example = "1")
        public Long staffId;
        @Schema(example = "Staff, Demo")
        public String staffName;
        @Schema(example = "2")
        public Long transferToOfficeId;
        @Schema(example = "Branch Office")
        public String transferToOfficeName;
        @Schema(example = "10")
        public Long imageId;
        @Schema(example = "8")
        public Long savingsAccountId;
        public GetClientsLegalForm legalForm;
        public GetClientsNonPerson clientNonPersonDetails;
    }

    @Schema(description = "PostClientsRequest")
    public static final class PostClientsRequest {

        private PostClientsRequest() {}

        static final class PostClientsDatatable {

            private PostClientsDatatable() {}

            @Schema(example = "Client Beneficiary information")
            public String registeredTableName;
            @Schema(example = "data")
            public HashMap<String, Object> data;

        }

        static final class PostClientsAddressRequest {

            @Schema(example = "Ipca")
            public String street;
            @Schema(example = "Kandivali")
            public String addressLine1;
            @Schema(example = "plot47")
            public String addressLine2;
            @Schema(example = "charkop")
            public String addressLine3;
            @Schema(example = "Mumbai")
            public String city;
            @Schema(example = "800")
            public Long stateProvinceId;
            @Schema(example = "802")
            public Long countryId;
            @Schema(example = "400064")
            public Long postalCode;
            @Schema(example = "1")
            public Long addressTypeId;
            @Schema(example = "true")
            public Boolean isActive;
        }

        @Schema(example = "1")
        public Long officeId;
        @Schema(example = "1")
        public Long legalFormId;
        @Schema(example = "Client of group")
        public String fullname;
        @Schema(example = "Client_FirstName")
        public String firstname;
        @Schema(example = "Client_MiddleName")
        public String middlename;
        @Schema(example = "123")
        public String externalId;
        @Schema(example = "Client_LastName")
        public String lastname;
        @Schema(example = "[2013, 1, 1]")
        public LocalDate dateOfBirth;
        @Schema(example = "1")
        public Long groupId;
        @Schema(example = "dd MMMM yyyy")
        public String dateFormat;
        @Schema(example = "en")
        public String locale;
        @Schema(example = "true")
        public Boolean active;
        @Schema(example = "04 March 2009")
        public String activationDate;
        @Schema(example = "04 March 2009")
        public String submittedOnDate;
        @Schema(example = "+353851239876")
        public String mobileNo;
        @Schema(description = "List of PostClientsDatatable")
        public List<PostClientsDatatable> datatables;
        @Schema(description = "Address requests")
        public List<ClientAddressRequest> address;
        @Schema(example = "test@test.com")
        public String emailAddress;
        @Schema(description = "Account number. Omitted to let the system generate one.", example = "000000028")
        public String accountNo;
        @Schema(description = "Tax identification number", example = "TIN-100200")
        public String taxIdentificationNumber;
        @Schema(description = "Alternate mobile number", example = "+353851111111")
        public String alternativeMobileNo;
        @Schema(description = "Alternate email address", example = "alt@test.com")
        public String alternativeEmailAddress;
        @Schema(description = "Sub-industry id", example = "4")
        public Long subIndustryId;
        @Schema(description = "Customer class id. Must match legalFormId.", example = "2")
        public Long customerClassId;
        @Schema(description = "Title id from m_client_title", example = "1")
        public Long titleId;
        @Schema(description = "Nationality code value id", example = "20")
        public Long nationalityCountryId;
        @Schema(description = "Customer risk profile code value id", example = "30")
        public Long customerRiskProfileId;
        @Schema(description = "Marital status code value id", example = "40")
        public Long maritalStatusId;
        @Schema(description = "Staff id to assign", example = "1")
        public Long staffId;
        @Schema(description = "Savings product used to open the default savings account when the client is activated", example = "1")
        public Long savingsProductId;
        @Schema(description = "Gender code value id", example = "15")
        public Long genderId;
        @Schema(description = "Client type code value id", example = "16")
        public Long clientTypeId;
        @Schema(description = "Client classification code value id", example = "17")
        public Long clientClassificationId;
        @Schema(description = "Display name override", example = "Ada Lovelace")
        public String displayname;
        @Schema(description = "True when this client is also a staff member", example = "false")
        public Boolean isStaff;
        @Schema(description = "Entity details. Required when legalFormId is an entity. Date incorpValidityTillDate is a string interpreted with dateFormat and locale.")
        public PostClientsNonPerson clientNonPersonDetails;
        @Schema(description = "Family members created with the client. dateOfBirth is a string interpreted with dateFormat and locale.")
        public List<PostClientsFamilyMember> familyMembers;
        @Schema(description = "Contacts created with the client")
        public List<PostClientsContact> contacts;
        @Schema(description = "Income sources created with the client. startDate and endDate are strings interpreted with dateFormat and locale.")
        public List<PostClientsIncomeSource> incomeSources;
        @Schema(description = "Compliance profile created with the client")
        public PostClientsComplianceProfile complianceProfile;
        @Schema(description = "Identifiers created with the client")
        public List<PostClientsIdentifier> clientIdentifiers;

        static final class PostClientsNonPerson {

            private PostClientsNonPerson() {}

            @Schema(example = "REG-100")
            public String incorpNumber;
            @Schema(example = "Notes")
            public String remarks;
            @Schema(description = "Date string interpreted with the parent dateFormat and locale", example = "31 December 2030")
            public String incorpValidityTillDate;
            @Schema(example = "12")
            public Long constitutionId;
            @Schema(example = "13")
            public Long mainBusinessLineId;
        }

        static final class PostClientsFamilyMember {

            private PostClientsFamilyMember() {}

            @Schema(example = "Grace")
            public String firstName;
            @Schema(example = "B")
            public String middleName;
            @Schema(example = "Hopper")
            public String lastName;
            @Schema(example = "PhD")
            public String qualification;
            @Schema(example = "+353850000000")
            public String mobileNumber;
            @Schema(example = "grace@example.com")
            public String emailAddress;
            @Schema(description = "Free-text address, up to 500 characters", example = "1 Main Street")
            public String address;
            @Schema(example = "40")
            public Integer age;
            @Schema(example = "false")
            public Boolean isDependent;
            @Schema(example = "22")
            public Long relationshipId;
            @Schema(example = "40")
            public Long maritalStatusId;
            @Schema(example = "15")
            public Long genderId;
            @Schema(description = "Date string interpreted with dateFormat and locale", example = "09 December 1906")
            public String dateOfBirth;
            @Schema(example = "18")
            public Long professionId;
            @Schema(example = "dd MMMM yyyy")
            public String dateFormat;
            @Schema(example = "en")
            public String locale;
        }

        static final class PostClientsContact {

            private PostClientsContact() {}

            @Schema(example = "1")
            public Long contactTypeId;
            @Schema(example = "+353851239876")
            public String contactValue;
            @Schema(example = "true")
            public Boolean primary;
        }

        static final class PostClientsIncomeSource {

            private PostClientsIncomeSource() {}

            @Schema(example = "1")
            public Long incomeSourceTypeId;
            @Schema(example = "2")
            public Long sourceOfFundsId;
            @Schema(example = "Acme Ltd")
            public String employerBusinessName;
            @Schema(example = "10 Dock Road")
            public String employerAddress;
            @Schema(example = "Engineer")
            public String occupation;
            @Schema(example = "4")
            public Long subIndustryId;
            @Schema(example = "2500.00")
            public java.math.BigDecimal monthlyIncome;
            @Schema(example = "USD")
            public String incomeCurrencyCode;
            @Schema(example = "3")
            public Long incomeFrequencyId;
            @Schema(description = "Date string interpreted with dateFormat and locale", example = "01 January 2020")
            public String startDate;
            @Schema(description = "Date string interpreted with dateFormat and locale", example = "31 December 2026")
            public String endDate;
            @Schema(example = "true")
            public Boolean isPrimarySource;
            @Schema(example = "5")
            public Long verificationStatusId;
            @Schema(example = "payslip.pdf")
            public String supportingDocument;
            @Schema(example = "Verified")
            public String remarks;
            @Schema(example = "ACTIVE")
            public String status;
            @Schema(example = "en")
            public String locale;
            @Schema(example = "dd MMMM yyyy")
            public String dateFormat;
        }

        static final class PostClientsOtherBankAccount {

            private PostClientsOtherBankAccount() {}

            @Schema(example = "Other Bank")
            public String bankName;
            @Schema(example = "Main")
            public String branchName;
            @Schema(example = "00112233")
            public String accountNumber;
            @Schema(example = "1")
            public Integer displayOrder;
        }

        static final class PostClientsComplianceProfile {

            private PostClientsComplianceProfile() {}

            @Schema(example = "false")
            public Boolean hasOtherBankAccounts;
            @Schema(example = "false")
            public Boolean isPep;
            @Schema(example = "Director")
            public String pepPosition;
            @Schema(example = "Jane Doe")
            public String pepRelativeName;
            @Schema(example = "false")
            public Boolean usCitizenOrResident;
            @Schema(example = "false")
            public Boolean fatcaRegistered;
            @Schema(example = "FATCA-1")
            public String fatcaRegistrationNo;
            @Schema(example = "Alt Bank")
            public String dpfAlternativeBankName;
            @Schema(example = "998877")
            public String dpfAlternativeAccountNumber;
            @Schema(description = "Up to two accounts, required when hasOtherBankAccounts is true")
            public List<PostClientsOtherBankAccount> otherBankAccounts;
            @Schema(example = "en")
            public String locale;
        }

        static final class PostClientsIdentifier {

            private PostClientsIdentifier() {}

            @Schema(example = "1")
            public Long documentTypeId;
            @Schema(example = "AB123456")
            public String documentKey;
            @Schema(example = "Active")
            public String status;
            @Schema(example = "National id")
            public String description;
        }
    }

    @Schema(description = "PostClientsResponse")
    public static final class PostClientsResponse {

        private PostClientsResponse() {}

        @Schema(example = "1")
        public Long officeId;
        @Schema(example = "1")
        public Long groupId;
        @Schema(example = "2")
        public Long clientId;
        @Schema(example = "2")
        public Long resourceId;
        @Schema(example = "123-456")
        public String resourceExternalId;
    }

    @Schema(description = "PutClientsClientIdRequest")
    public static final class PutClientsClientIdRequest {

        private PutClientsClientIdRequest() {}

        @Schema(example = "786444UUUYYH7")
        public String externalId;
        @Schema(example = "123-456")
        public String resourceExternalId;
        @Schema(example = "Client_FirstName")
        public String firstname;
        @Schema(example = "Client_LastName")
        public String lastname;
        @Schema(example = "Client Middle")
        public String middlename;
        @Schema(example = "Client Full Name")
        public String fullname;
        @Schema(example = "000000027")
        public String accountNo;
        @Schema(example = "+353851239876")
        public String mobileNo;
        @Schema(example = "test@test.com")
        public String emailAddress;
        @Schema(description = "Tax identification number", example = "TIN-100200")
        public String taxIdentificationNumber;
        @Schema(description = "Alternate mobile number", example = "+353851111111")
        public String alternativeMobileNo;
        @Schema(description = "Alternate email address", example = "alt@test.com")
        public String alternativeEmailAddress;
        @Schema(description = "Sub-industry id", example = "4")
        public Long subIndustryId;
        @Schema(description = "Customer class id", example = "2")
        public Long customerClassId;
        @Schema(description = "Title id from m_client_title", example = "1")
        public Long titleId;
        @Schema(description = "Nationality code value id", example = "20")
        public Long nationalityCountryId;
        @Schema(description = "Customer risk profile code value id", example = "30")
        public Long customerRiskProfileId;
        @Schema(description = "Marital status code value id", example = "40")
        public Long maritalStatusId;
        @Schema(description = "Date string interpreted with dateFormat and locale", example = "04 March 2009")
        public String activationDate;
        @Schema(description = "Date string interpreted with dateFormat and locale", example = "04 March 2009")
        public String submittedOnDate;
        @Schema(description = "Date string interpreted with dateFormat and locale", example = "04 March 1990")
        public String dateOfBirth;
        @Schema(example = "dd MMMM yyyy")
        public String dateFormat;
        @Schema(example = "en")
        public String locale;
        @Schema(example = "true")
        public Boolean active;
        @Schema(example = "1")
        public Long staffId;
        @Schema(example = "1")
        public Long savingsProductId;
        @Schema(example = "15")
        public Long genderId;
        @Schema(example = "16")
        public Long clientTypeId;
        @Schema(example = "17")
        public Long clientClassificationId;
        @Schema(example = "Ada Lovelace")
        public String displayname;
        @Schema(example = "false")
        public Boolean isStaff;
        public PostClientsRequest.PostClientsNonPerson clientNonPersonDetails;
    }

    @Schema(description = "PutClientsClientIdResponse")
    public static final class PutClientsClientIdResponse {

        private PutClientsClientIdResponse() {}

        @Schema(example = "1")
        public Long officeId;
        @Schema(example = "1")
        public Long clientId;
        @Schema(example = "1")
        public Long resourceId;
        @Schema(example = "123-456")
        public String resourceExternalId;
        public PutClientsClientIdRequest changes;
    }

    @Schema(description = "DeleteClientsClientIdRequest")
    public static final class DeleteClientsClientIdRequest {

        private DeleteClientsClientIdRequest() {}
    }

    @Schema(description = "DeleteClientsClientIdResponse")
    public static final class DeleteClientsClientIdResponse {

        private DeleteClientsClientIdResponse() {}

        @Schema(example = "1")
        public Long officeId;
        @Schema(example = "3")
        public Long clientId;
        @Schema(example = "3")
        public Long resourceId;
        @Schema(example = "123-456")
        public String resourceExternalId;
    }

    @Schema(description = "PostClientsClientIdRequest")
    public static final class PostClientsClientIdRequest {

        private PostClientsClientIdRequest() {}

        @Schema(example = "03 August 2021")
        public String activationDate;
        @Schema(example = "dd MMMM yyyy")
        public String dateFormat;
        @Schema(example = "en")
        public String locale;
        @Schema(example = "03 August 2021")
        public String closureDate;
        @Schema(example = "1")
        public Long closureReasonId;
        @Schema(example = "03 August 2021")
        public String reactivationDate;
        @Schema(example = "03 August 2021")
        public String rejectionDate;
        @Schema(example = "1")
        public Long rejectionReasonId;
        @Schema(example = "03 August 2021")
        public String withdrawalDate;
        @Schema(example = "1")
        public Long withdrawalReasonId;
        @Schema(example = "03 August 2021")
        public String reopenedDate;
        @Schema(example = "1")
        public Long staffId;
    }

    @Schema(description = "PostClientsClientIdResponse")
    public static final class PostClientsClientIdResponse {

        private PostClientsClientIdResponse() {}

        static final class PostClientsClientIdChanges {

            private PostClientsClientIdChanges() {}

            @Schema(example = "1")
            public Long staffId;
        }

        @Schema(example = "2")
        public Long officeId;
        @Schema(example = "2")
        public Long clientId;
        @Schema(example = "2")
        public Long resourceId;
        @Schema(example = "123-456")
        public String resourceExternalId;
        public PostClientsClientIdChanges changes;
    }

    @Schema(description = "GetClientTransferProposalDateResponse")
    public static final class GetClientTransferProposalDateResponse {

        @Schema(example = "[2013, 1, 1]")
        public LocalDate proposedTransferDate;
    }

    @Schema(description = "GetObligeeData")
    public static final class GetObligeeData {

        private GetObligeeData() {}

        @Schema(example = "John")
        public String firstName;
        @Schema(example = "Doe")
        public String lastName;
        @Schema(example = "John Doe")
        public String displayName;
        @Schema(example = "00000002")
        public String accountNumber;
        @Schema(example = "12000.00")
        public Double loanAmount;
        @Schema(example = "100.00")
        public Double guaranteeAmount;
        @Schema(example = "10000.00")
        public Double amountReleased;
        @Schema(example = "1000.00")
        public Double amountTransferred;
    }

    @Schema(description = "GetClientObligeeDetailsResponse")
    public static final class GetClientObligeeDetailsResponse {

        private GetClientObligeeDetailsResponse() {}

        public Set<GetObligeeData> obligees;
    }

    @Schema(description = "GetClientsClientIdAccountsResponse")
    public static final class GetClientsClientIdAccountsResponse {

        private GetClientsClientIdAccountsResponse() {}

        static final class GetClientsLoanAccounts {

            private GetClientsLoanAccounts() {}

            static final class GetClientsLoansAccountsCurrency {

                private GetClientsLoansAccountsCurrency() {}

                @Schema(example = "USD")
                public String code;
                @Schema(example = "US Dollar")
                public String name;
                @Schema(example = "2")
                public Integer decimalPlaces;
                @Schema(example = "$")
                public String displaySymbol;
                @Schema(example = "currency.USD")
                public String nameCode;
                @Schema(example = "US Dollar ($)")
                public String displayLabel;
            }

            static final class GetClientsLoanAccountsStatus {

                private GetClientsLoanAccountsStatus() {}

                @Schema(example = "300")
                public Long id;
                @Schema(example = "loanStatusType.active")
                public String code;
                @Schema(example = "Active")
                public String description;
                @Schema(example = "false")
                public Boolean pendingApproval;
                @Schema(example = "false")
                public Boolean waitingForDisbursal;
                @Schema(example = "true")
                public Boolean active;
                @Schema(example = "false")
                public Boolean closedObligationsMet;
                @Schema(example = "false")
                public Boolean closedWrittenOff;
                @Schema(example = "false")
                public Boolean closedRescheduled;
                @Schema(example = "false")
                public Boolean closed;
                @Schema(example = "false")
                public Boolean overpaid;
            }

            static final class GetClientsLoanAccountsType {

                private GetClientsLoanAccountsType() {}

                @Schema(example = "1")
                public Long id;
                @Schema(example = "loanType.individual")
                public String code;
                @Schema(example = "Individual")
                public String description;
            }

            @Schema(example = "1")
            public Long id;
            @Schema(example = "000000001")
            public String accountNo;
            @Schema(example = "456")
            public String externalId;
            @Schema(example = "1")
            public Long productId;
            @Schema(example = "TestOne")
            public String productName;
            public GetClientsLoansAccountsCurrency currency;
            public GetClientsLoanAccountsStatus status;
            public GetClientsLoanAccountsType loanType;
            @Schema(example = "1")
            public Integer loanCycle;
        }

        static final class GetClientsSavingsAccounts {

            private GetClientsSavingsAccounts() {}

            static final class GetClientsSavingsAccountsCurrency {

                private GetClientsSavingsAccountsCurrency() {}

                @Schema(example = "USD")
                public String code;
                @Schema(example = "US Dollar")
                public String name;
                @Schema(example = "2")
                public Integer decimalPlaces;
                @Schema(example = "$")
                public String displaySymbol;
                @Schema(example = "currency.USD")
                public String nameCode;
                @Schema(example = "US Dollar ($)")
                public String displayLabel;
            }

            static final class GetClientsSavingsAccountsStatus {

                private GetClientsSavingsAccountsStatus() {}

                @Schema(example = "100")
                public Long id;
                @Schema(example = "savingsAccountStatusType.submitted.and.pending.approval")
                public String code;
                @Schema(example = "Submitted and pending approval")
                public String value;
                @Schema(example = "true")
                public Boolean submittedAndPendingApproval;
                @Schema(example = "false")
                public Boolean approved;
                @Schema(example = "false")
                public Boolean rejected;
                @Schema(example = "false")
                public Boolean withdrawnByApplicant;
                @Schema(example = "false")
                public Boolean active;
                @Schema(example = "false")
                public Boolean closed;
                @Schema(example = "false")
                public Boolean prematureClosed;
                @Schema(example = "false")
                public Boolean transferInProgress;
                @Schema(example = "false")
                public Boolean transferOnHold;
                @Schema(example = "false")
                public Boolean matured;
            }

            static final class GetClientsSavingsAccountsDepositType {

                private GetClientsSavingsAccountsDepositType() {}

                @Schema(example = "100")
                public Long id;
                @Schema(example = "depositAccountType.savingsDeposit")
                public String code;
                @Schema(example = "Savings")
                public String value;
            }

            @Schema(example = "7")
            public Long id;
            @Schema(example = "000000007")
            public String accountNo;
            @Schema(example = "2")
            public Long productId;
            @Schema(example = "Other product")
            public String productName;
            @Schema(example = "OP")
            public String shortProductName;
            public GetClientsSavingsAccountsStatus status;
            public GetClientsSavingsAccountsCurrency currency;
            public GetClientsSavingsAccountsDepositType depositType;
        }

        static final class GetClientsWorkingCapitalLoanAccounts {

            private GetClientsWorkingCapitalLoanAccounts() {}

            static final class GetClientsWorkingCapitalLoanAccountsStatus {

                private GetClientsWorkingCapitalLoanAccountsStatus() {}

                @Schema(example = "100")
                public Long id;
                @Schema(example = "loanStatusType.submitted.and.pending.approval")
                public String code;
                @Schema(example = "Submitted and pending approval")
                public String value;
            }

            static final class GetClientsWorkingCapitalLoanAccountsCurrency {

                private GetClientsWorkingCapitalLoanAccountsCurrency() {}

                @Schema(example = "USD")
                public String code;
                @Schema(example = "US Dollar")
                public String name;
                @Schema(example = "2")
                public Integer decimalPlaces;
                @Schema(example = "$")
                public String displaySymbol;
                @Schema(example = "currency.USD")
                public String nameCode;
                @Schema(example = "US Dollar ($)")
                public String displayLabel;
            }

            @Schema(example = "1")
            public Long id;
            @Schema(example = "WCL-1")
            public String accountNo;
            @Schema(description = "Parent account number, null for working capital loans")
            public String parentAccountNumber;
            @Schema(example = "ext-wcl-001")
            public String externalId;
            @Schema(example = "1")
            public Long productId;
            @Schema(example = "Working Capital Product 1")
            public String productName;
            @Schema(example = "WCP1")
            public String shortProductName;
            public GetClientsWorkingCapitalLoanAccountsStatus status;
            public GetClientsWorkingCapitalLoanAccountsCurrency currency;
            @Schema(description = "Loan type, null for working capital loans")
            public Object loanType;
            @Schema(description = "Loan cycle, null for working capital loans")
            public Integer loanCycle;
            @Schema(description = "Timeline (submittedOnDate, approvedOnDate, etc.)")
            public Object timeline;
            @Schema(example = "false")
            public Boolean inArrears;
            @Schema(example = "10000.00")
            public java.math.BigDecimal originalLoan;
            @Schema(example = "10000.00")
            public java.math.BigDecimal loanBalance;
            @Schema(example = "0")
            public java.math.BigDecimal amountPaid;
        }

        public Set<GetClientsLoanAccounts> loanAccounts;
        public Set<GetClientsSavingsAccounts> savingsAccounts;
        public Set<GetClientsWorkingCapitalLoanAccounts> workingCapitalLoanAccounts;
    }
}
