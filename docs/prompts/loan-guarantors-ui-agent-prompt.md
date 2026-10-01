# UI Agent Prompt: Loan guarantors

Copy everything below the line into the coding agent working on the frontend repository (`mifos-web-next`).

---

## Task

Build the loan guarantor screens from the contract below: list, add, edit, remove, and the savings pledge. Extend the existing loan account. Do not rebuild loan navigation.

Guarantors are not an association on `GET /v1/loans/{loanId}`. Load them from the guarantor resource.

## When the actions exist

Show add, edit, and delete only while the loan status is **Submitted and pending approval**, **Approved**, or **Active**. Any other status returns `loan.guarantor.loan.is.closed`.

| Permission | Use |
|---|---|
| `READ_GUARANTOR` | List, detail, templates |
| `CREATE_GUARANTOR` | Add |
| `UPDATE_GUARANTOR` | Edit |
| `DELETE_GUARANTOR` | Remove |
| `RECOVERGUARANTEES_LOAN` | Recover pledged savings onto the loan |

Create, update, and delete are maker-checker commands. A success response may be a pending command. Once applied, `resourceId` is the guarantor id.

## Endpoints

Base path: `/v1/loans/{loanId}/guarantors`. The path `loanId` is the loan. A `loanId` in the JSON body is ignored.

| Action | Call |
|---|---|
| Form options | `GET .../guarantors/template` |
| Savings the selected client can pledge | `GET .../guarantors/accounts/template?clientId={clientId}` |
| List | `GET .../guarantors` |
| One guarantor, with options for edit | `GET .../guarantors/{guarantorId}?template=true` |
| Create | `POST .../guarantors` |
| Update | `PUT .../guarantors/{guarantorId}` |
| Remove the person | `DELETE .../guarantors/{guarantorId}` |
| Remove one pledge | `DELETE .../guarantors/{guarantorId}?guarantorFundingId={id}` |
| Loans this client has guaranteed | `GET /v1/clients/{clientId}/obligeedetails` |
| Pull pledged savings into the loan | `POST /v1/loans/{loanId}?command=recoverGuarantees` |

Dates in responses are `[year, month, day]`. Send `dob` with `dateFormat` and `locale`. Send `amount` with `locale`. `guarantorTypeId` is an integer and does not use `locale`.

## Guarantor type

Load the type dropdown from `guarantorTypeOptions` on the template. The empty template preselects **Customer**. Show `value` as the label. Send `id`.

| `guarantorTypeId` | `code` | `value` | `entityId` |
|---|---|---|---|
| 1 | `guarantor.existing.customer` | `CUSTOMER` | Client id |
| 2 | `guarantor.staff` | `STAFF` | Staff id |
| 3 | `guarantor.external` | `EXTERNAL` | Omit it |
| 4 | `guarantor.existing.group` | `GROUP` | Group id |

Relationship is optional for every type. Options are `allowedClientRelationshipTypes` (code **GuarantorRelationship**). Send `clientRelationshipTypeId`.

## Add form

**Customer, staff, or group.** Require `entityId`. Hide name, address, and national ID. The server fills the client or staff name on read.

**External.** Require `firstname`, `lastname`, and `nationalIdNumber`. The other personal fields are optional.

| Field | Max length | Rule |
|---|---|---|
| `firstname`, `lastname` | 50 | Required for external |
| `nationalIdNumber` | 50 | Required for external. Plain text. Label **National ID Number** |
| `addressLine1`, `addressLine2` | 500 | |
| `city`, `state`, `country` | 50 | |
| `zip` | 20 | |
| `mobileNumber`, `housePhoneNumber` | 20 | `^\+?[0-9. ()-]{0,25}$` |
| `comment` | 500 | |
| `dob` | date | `"dateFormat": "dd MMMM yyyy"`, `"locale": "en"` |

`nationalIdNumber` is the KYC identifier for a person who is not a client. Customer, staff, and group guarantors already have an `entityId`. Do not show the national ID field for those types, and do not send it.

External create:

```json
{
  "guarantorTypeId": 3,
  "firstname": "Amina",
  "lastname": "Okello",
  "nationalIdNumber": "CM1234567890123",
  "mobileNumber": "+256700000000",
  "city": "Kampala",
  "clientRelationshipTypeId": 3,
  "locale": "en",
  "dateFormat": "dd MMMM yyyy",
  "dob": "04 March 1983"
}
```

## Savings pledge

Show the pledge when the loan product has `holdGuaranteeFunds: true`. Read the thresholds from that product's `productGuaranteeData`. Each value is a **percentage of the loan principal**.

| Product field | Whose pledges count |
|---|---|
| `minimumGuaranteeFromOwnFunds` | The borrower, as a customer guarantor, pledging their own savings |
| `minimumGuaranteeFromGuarantor` | Every other pledge |
| `mandatoryGuarantee` | Both of those added together |

A guarantor with no `savingsId` counts as zero. Show the shortfall on the form. The server enforces the percentages at **approval**, and again when a pledge is removed from an approved or active loan.

After the user picks a client, load `accounts/template?clientId=`. That list is present only when `holdGuaranteeFunds` is true. It is the client's **active** savings, including fixed and recurring deposits. Use `id` as `savingsId`. Show `accountNo`, `productName`, and `currency`.

`savingsId` and `amount` travel together. If a savings account is selected, `amount` is required, must be greater than zero, and needs `locale`. The savings account must have been activated on or before the loan `submittedOnDate`. Available balance is checked when the funds are held: at approval, or immediately if the loan is already approved or active. A short balance fails with `loan.guarantor.insufficient.balance`.

Posting the same client, staff member, or group again with a **different** savings account adds another funding line on the existing guarantor. The same person plus the same savings account is a duplicate.

### Own savings

A customer guarantor whose `entityId` is the borrower's client id, with a savings pledge and no relationship, is an own-funds guarantee.

- Borrower plus savings, relationship empty: allowed.
- Borrower with no savings: `guarantor.can.not.be.own`.
- Borrower plus savings plus a relationship: `guarantor.relation.should.be.empty.for.own`.

A group loan has no borrower client, so there is no own-savings path.

Borrower pledging their own savings:

```json
{
  "guarantorTypeId": 1,
  "entityId": 15,
  "savingsId": 88,
  "amount": 5000,
  "locale": "en"
}
```

## What edit can change

`PUT` persists:

- `clientRelationshipTypeId` for every type
- the personal fields, including `nationalIdNumber`, only when the **stored** type is already external

Type, `entityId`, `savingsId`, and `amount` are not updated. To pledge another account, `POST` again for the same person. To change the person or the type, remove and create again.

The update body must include at least one of `entityId`, `firstname`, `lastname`, `nationalIdNumber`, an address field, a phone, or `comment`. For an external guarantor, send the name and national ID back with any other change. Guarantors created before national ID existed can have a null number: show an empty field and require a value before saving the edit.

## List and detail

`status` is the guarantor's active flag. Inactive rows stay in the list. Show them as removed.

```json
{
  "id": 9,
  "loanId": 4,
  "status": true,
  "guarantorType": { "id": 3, "code": "guarantor.external", "value": "EXTERNAL" },
  "entityId": null,
  "clientRelationshipType": { "id": 3, "name": "Sibling" },
  "firstname": "Amina",
  "lastname": "Okello",
  "nationalIdNumber": "CM1234567890123",
  "guarantorFundingDetails": [
    {
      "id": 3,
      "status": { "id": 100, "code": "guarantorFundStatusType.active", "value": "ACTIVE" },
      "savingsAccount": { "id": 88, "accountNo": "000000088" },
      "amount": 5000,
      "amountReleased": 0,
      "amountRemaining": 5000,
      "amountTransfered": 0,
      "guarantorTransactions": []
    }
  ]
}
```

How to label the row:

- **Customer:** `firstname` + `lastname`, plus `officeName`, `joinedDate` (client activation date), and `externalId`. Address and national ID are empty.
- **Staff:** `firstname` + `lastname` and `officeName`.
- **Group:** name fields are empty. Load the group with `entityId` and show the group name.
- **External:** stored name, national ID, and address.

Funding line status:

| `status.id` | Meaning | UI |
|---|---|---|
| 100 | Active hold | Can be removed |
| 200 | Completed, fully released | Read only |
| 300 | Withdrawn after approval or disbursement | Read only |
| 400 | Deleted while the loan was still pending approval | Read only |

Each `guarantorTransactions` entry has `id`, `reversed`, and `onHoldTransactionData` (`amount`, `transactionDate`, `transactionType`, `reversed`). `transactionType.id` is `1` for a hold and `2` for a release.

## Delete

- No funding lines: `DELETE` the guarantor. `status` becomes false. The row remains in the list.
- One or more funding lines: delete each line with `guarantorFundingId`. Deleting the person without that query param returns guarantor-not-found. On a pending loan the line becomes **Deleted**. On an approved or active loan the line becomes **Withdrawn**, the savings hold is released, and the product percentages are checked again.

## Other screens

**Client obligee list** (`GET /v1/clients/{clientId}/obligeedetails`, permission `READ_CLIENT`). One row per loan this client has pledged savings for. Fields: borrower `firstName`, `lastName`, `displayName`, loan `accountNumber`, `loanAmount`, `guaranteeAmount`, `amountReleased`, `amountTransferred`. Staff, group, and external guarantors are not in this list.

**Recover guarantees** is a loan action, `POST /v1/loans/{loanId}?command=recoverGuarantees`, with an empty body. It transfers each active remaining pledge from savings onto the loan as a repayment, then releases that guarantor. Show it on an active loan that still has `amountRemaining` greater than zero.

## Out of scope

- Guarantor bulk import (`downloadtemplate` / `uploadtemplate`).
- Working capital loans.
- Editing type, person, savings account, or pledged amount on an existing guarantor.
- Showing national ID on customer, staff, or group guarantors.
