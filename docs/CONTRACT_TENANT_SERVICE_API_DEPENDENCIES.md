# Contract Tenant Service API Dependencies

## Purpose

This is the minimal integration handoff for teams that provide or consume `contract-tenant-service`. It lists only synchronous APIs found in the service's actual Feign call sites, the internal APIs Contract exposes to peers, and the Kafka contracts used instead of more synchronous calls. It is not a complete API catalog for Property, Listing, or Billing.

The goal is to keep the dependency graph one-way where possible. Do not add REST calls from Contract to Listing or Billing for workflows already represented by Kafka events or Contract's internal read APIs.

## Synchronous APIs Contract calls

### 1. Property room capacity

| Item | Contract |
|---|---|
| Caller → owner | `contract-tenant-service` → `property-service` |
| Method and path | `GET /internal/rooms/{roomId}` |
| Called for | Processing `RoommateApplicationAccepted`: read room capacity before adding a tenant |
| Required response fields | `200 { "id": 42, "buildingId": 7, "roomNumber": "A.402", "maxCapacity": 3, "status": "OCCUPIED" }` |
| Errors | `404 NOT_FOUND`; timeout, 5xx, or unavailable dependency becomes `503 DEPENDENCY_UNAVAILABLE` and the tenant addition fails closed |
| Owner status | Property team already owns this room lookup. Do not create a duplicate endpoint or ask for another room-detail API. Contract only requires `maxCapacity` (and retains `id`/`buildingId` for compatibility). |

`ContractService.addTenant` uses `maxCapacity` to reject a full room. Do not return a default capacity when the room or capacity cannot be read.

### 2. Staff building assignments

| Item | Contract |
|---|---|
| Caller → owner | `contract-tenant-service` → `user-notification-service` |
| Method and path | `GET /internal/staff/{staffId}/buildings` |
| Called for | Building-scope authorization on staff contract and move-out operations; ADMIN skips this call |
| Required response fields | `200 { "staffId": 101, "buildingIds": [7, 9] }` |
| Errors | `404 NOT_FOUND` for unknown staff; timeout, 5xx, missing response, or missing `buildingIds` becomes `503 DEPENDENCY_UNAVAILABLE` and authorization fails closed |
| Owner status | Already implemented in `user-notification-service`; no new endpoint is needed. Keep the response shape compatible with `asset-maintenance-service`'s User client. |

The caller passes the current user's ID as `staffId`. For a staff list query with no `buildingId`, Contract uses this list to constrain the query. Never interpret an unavailable assignment list as unrestricted access.

## Internal APIs Contract exposes to peers

These endpoints are implemented by Contract and are the only synchronous reads other services need from it under the current specification.

| Consumer | Method and path | Response / purpose |
|---|---|---|
| Billing | `GET /internal/contracts/billing-data?month={month}&year={year}` | `200 [BillingContractData]`; effective contracts with rent, dates, and each tenant's `joinedAt`, `leftAt`, and `isRepresentative`, for monthly proration/splitting |
| Listing | `GET /internal/rooms/{roomId}/tenants/{customerId}` | `200 { "isTenant": true, "contractId": 15, "buildingId": 7 }`; check whether a customer is a tenant in an ACTIVE contract before creating a roommate post |

Both are service-to-service endpoints. API Gateway must not expose `/internal/**` to clients. Consumers must not call these in a way that synchronously calls back into them as part of the same request chain.

## Kafka instead of synchronous calls

Contract consumes Listing events:

| Topic | Event | Contract action |
|---|---|---|
| `listing.events` | `RentalRequestAccepted` | Create a DRAFT contract and representative tenant idempotently |
| `listing.events` | `RoommateApplicationAccepted` | Validate active contract/capacity, then add tenant or publish rejection |

Contract publishes on `contract.events`:

| Event | Main consumers | Purpose |
|---|---|---|
| `ContractSigned` | Property, Listing, Billing, Notification | Mark room occupied, close listing and pending requests, update billing contract snapshot, notify tenants |
| `ContractRenewed` | Billing, Notification | Update contract end date snapshot and notify tenants |
| `ContractEnded` | Property, Listing, Billing, Notification | Release room/listing state and update billing snapshot |
| `TenantAdded`, `TenantAddRejected` | Listing, Notification | Finalize roommate application result |
| `TenantRemoved`, `MoveOutRequested`, `MoveOutApproved` | Notification; Property also consumes `MoveOutApproved` | Notify affected users and update room availability |

All events use the shared envelope and outbox/idempotency rules in system-spec sections 5.3 and 10. Listing and Billing should consume Contract events rather than poll Contract endpoints for every state change. Billing's monthly job may call `billing-data` once per month/run to get the occupancy intervals it needs.

## Dependency direction

```text
Contract --GET room capacity--> Property
Contract --GET staff buildings--> User/Staff
Listing --GET tenant check--> Contract
Billing --GET monthly billing data--> Contract
Listing --Kafka events--> Contract
Contract --Kafka events--> Property, Listing, Billing, Notification
```

There is no synchronous Contract → Listing or Contract → Billing call in the current implementation. Do not add one for state that Contract already receives by Kafka or exposes through the two internal read endpoints above.

## Shared call requirements

- Services discover each other by Eureka service name; use OpenFeign DTOs containing only fields the caller needs.
- Propagate `X-User-Id`, `X-User-Type`, `X-User-Roles`, and `X-Correlation-Id`. Scheduler calls use `X-User-Type: SYSTEM`.
- Use 2-second connect and 3-second read timeouts. Do not retry writes; GET may retry at most once.
- Map 404 to not found, other 4xx to the business error, and timeout/5xx/open circuit to `503 DEPENDENCY_UNAVAILABLE`.
- Internal APIs stay inside the service network and return the shared error shape. Do not return mock/default data on dependency failure.

## Source and ownership

Source of truth: `docs/Đặc tả Hệ thống Microservices – Quản lý Cho thuê Phòng.docx`, sections 5.2, 5.3, 6.2, 9.2, 9.3, 9.4, and 10. Ownership/status above was checked against the current `contract-tenant-service` Feign clients and `user-notification-service` controller. Resolve any interface change with the owning team before changing the client contract.
