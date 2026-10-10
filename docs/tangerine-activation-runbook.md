# Tangerine Activation Runbook

Scope: releasing, verifying and, if needed, reversing the activation of the
Tangerine Money-Back Credit Card in CardWise AI's REAL catalogue.

This document contains no credentials. Connection details belong in the hosting
provider's secret store, never in this repository.

## 1. What changes and what does not

| Migration | Effect |
|---|---|
| V10 | Adds `reward_rules.is_selectable`, `reward_rules.unselected_reward_rate` and the `card_selection_policies` table |
| V11 | Inserts Tangerine with `catalogue_withheld = TRUE`, its policy and 8 reward rules |
| V12 | Validates Tangerine's records, then sets `catalogue_withheld = FALSE` for exactly one row, or fails and rolls back |

Rates are stored as percentages (`2.0000` = 2%). V10 to V12 must never be edited
or deleted once applied anywhere: Flyway checksums them.

**Activation is a data change.** Reverting application code does not hide
Tangerine again. Recovery is a forward-only change (section 5).

## 2. Hazards to understand before releasing

1. **Starting a V12-enabled build activates Tangerine.** Flyway runs on startup.
   To deploy code without activating, hold Flyway at V11 (section 3, step 2).
2. **Older application builds must not serve traffic after V11 is applied.**
   Builds from before the selection-aware commit do not know about
   `catalogue_withheld` or selectable rules. Against a V11 database they would
   list Tangerine in REAL mode and apply 2% to all four selectable categories.
   Drain or stop every old instance before the first migration step, or use
   blue/green so old instances never share a migrated database.
3. **Activation takes effect for every instance at once.** The catalogue is read
   from the database on each request, so one instance running V12 activates the
   card for all instances. All instances must run a compatible build first.
4. **A manual rollback leaves Flyway history at V12.** Restarting instances will
   not reactivate Tangerine, but a brand-new database built from V1 will run V12
   and be active (see section 5).

## 3. Deployment sequence

### Step 1: Preflight
- Record the target environment, the database, the application version
  currently serving, and the Flyway version in `flyway_schema_history`.
- Confirm a verified backup exists. Take one with the provider's snapshot
  feature or `pg_dump`, and confirm it can be restored.
- Confirm who may run migrations and who may run emergency SQL, and that they are
  reachable during the release window.
- Confirm environment variables are set (section 6).
- Confirm no instance still runs a pre-selection-aware build (hazard 2).

### Step 2: Deploy compatible code with Flyway held at V11
Deploy the V12-enabled build with `SPRING_FLYWAY_TARGET=11`.

Flyway then applies V10 and V11 only; V12 stays pending and Tangerine stays
withheld. This was verified on a throwaway database:

```
Successfully validated 12 migrations
Successfully applied 11 migrations to schema "public", now at version v11
```

Gate checks (all must pass before step 3):
- `GET /actuator/health` returns `UP` on every instance.
- `GET /api/v1/cards` lists RBC only.
- `GET /api/v1/recommendations?groceries=600&gas=200&dining=300&travel=100&other=400`
  returns RBC at $216.00 and nothing else.
- `SELECT max(version::int) FROM flyway_schema_history;` is `11`.

### Step 3: Activate Tangerine
Restart one instance without `SPRING_FLYWAY_TARGET` (or set it to `12`). Flyway
applies V12:

```
Migrating schema "public" to version "12 - activate tangerine money back"
Successfully applied 1 migration to schema "public", now at version v12
```

Instances still started with `SPRING_FLYWAY_TARGET=11` start cleanly against a V12
database (verified on a throwaway database). That only proves startup: smoke-test
every instance, gated or not, against the activated database (step 4) before
relying on a mixed rollout. `SPRING_FLYWAY_TARGET` controls migration execution
only; it is not a feature flag and does not hide Tangerine on a database that is
already at V12. Remove the variable from the rest at their next restart.

If V12's validation fails, Flyway fails the migration and the transaction rolls
back. Nothing is activated, and the failing message begins
`V12 activation blocked:`. Do not edit V12 to get past it. Investigate the data
(section 7) and decide on a forward fix.

### Step 4: Production smoke test
Use the reference profile `groceries=600&gas=200&dining=300&travel=100&other=400`.
Find Tangerine's id from `GET /api/v1/cards`; do not hardcode it.

| Check | Expected |
|---|---|
| `/api/v1/cards` | RBC and Tangerine; no `catalogueWithheld` field; Tangerine has `selectionPolicy` |
| Recommendations, no selection | RBC $216.00; Tangerine $96.00, `selectionMode: NONE_SELECTED`, `selectionRequired: true` |
| `&cardId=<id>&selectedCategories=GROCERIES` | Tangerine $204.00, `PARTIAL_SELECTION`, incomplete |
| `&cardId=<id>&selectedCategories=GROCERIES,GAS` | Tangerine $240.00, `SELECTED`, complete |
| `&selectedCategories=GROCERIES,GAS,DINING&extendedRequirementConfirmed=true` | Tangerine $294.00, complete |
| Same three categories with confirmation `false` | HTTP 400 |
| Break-even, RBC vs Tangerine, Groceries + Gas | `NO_CROSSOVER` |
| Simulation, Groceries + Gas, first point (groceries 0) | Tangerine 96.0, RBC 72.0 |
| UI, no selection | Provisional banner, "Incomplete estimate" badge, no Best Match |
| UI, two categories | Tangerine marked Best Match, ranking numbers shown |
| Instances | Every instance healthy |

### Step 5: Monitor
Watch application errors, database errors, API latency, and the rate of 4xx and
5xx on `/api/v1/recommendations`, `/api/v1/simulations/groceries` and
`/api/v1/break-even`. Note that `invalid selection` 400 responses are expected
user input errors, not incidents.

### Step 6: Recover if necessary
Stop any further rollout, apply the rollback (section 5), verify Tangerine is
hidden, and investigate before attempting reactivation.

## 4. Expected outcomes by state

| Scenario | Expected outcome |
|---|---|
| Before V12 | RBC visible, Tangerine withheld |
| After V12 | RBC and Tangerine visible |
| Tangerine with no selection | $96.00, incomplete |
| Tangerine with Groceries + Gas | $240.00, complete |
| Three selections without confirmation | HTTP 400 |
| DEMO mode (`CARDWISE_CATALOGUE_MODE=DEMO`) | The three original demo cards only |
| After rollback | Tangerine hidden again; RBC $216.00 unchanged |
| Failed migration | No partial activation; no V12 row in `flyway_schema_history` |

## 5. Rollback

There is no application-level switch. Tangerine is hidden or shown only by
`credit_cards.catalogue_withheld`.

| Option | Use |
|---|---|
| **New forward-only migration (V13)** | Preferred for controlled deployments |
| Manual SQL (same statement) | Emergency containment only, with authorization and an audit record |
| Restore a full database backup | Last resort; can overwrite unrelated data |
| Revert the application commit alone | Insufficient: the data change persists |

### Rollback SQL template
This is a recovery template. It is **not** a migration in the repository and must
not be executed against a persistent database without authorization. Prepare it
as `V13__withhold_tangerine_money_back.sql` only if needed.

```sql
DO $$
DECLARE
    target_id BIGINT;
    affected_rows INTEGER;
BEGIN
    SELECT id INTO STRICT target_id
    FROM credit_cards
    WHERE issuer = 'Tangerine'
      AND card_name = 'Tangerine Money-Back Credit Card'
      AND is_demo = FALSE;

    UPDATE credit_cards
    SET catalogue_withheld = TRUE
    WHERE id = target_id;

    GET DIAGNOSTICS affected_rows = ROW_COUNT;

    IF affected_rows <> 1 THEN
        RAISE EXCEPTION
            'Tangerine rollback failed: expected 1 row, got %',
            affected_rows;
    END IF;
END $$;
```

`SELECT INTO STRICT` raises if the card is missing or duplicated, and the
surrounding transaction rolls back. Reviewed on a disposable database:

| Case | Result |
|---|---|
| Card present once | `DO`; Tangerine hidden at once; RBC $216.00; Flyway history still at 12 |
| Card missing | `ERROR: query returned no rows`; nothing changed |
| Card duplicated | `ERROR: query returned more than one row`; nothing changed |

### After rolling back
- `SELECT catalogue_withheld FROM credit_cards WHERE issuer = 'Tangerine';` is `t`
  and exactly that one card is withheld.
- `/api/v1/cards` lists RBC only, with no Tangerine and no `selectionPolicy`.
- `/api/v1/recommendations` returns RBC at $216.00.
- Demo catalogue (DEMO mode) still returns the three original demo cards.
- Every instance reports `UP` on `/actuator/health`.
- No instance needs restarting for the change to take effect.

### Reactivation and new environments
- If the rollback was manual SQL, V12 stays recorded as applied, so restarts do
  not reactivate. Reactivate deliberately, with a reviewed migration or SQL.
- A new database built from V1 runs V12 and is active. Once a V13 exists, a new
  database ends up withheld again (V12 then V13).

## 6. Environment and platform notes

Variables the application reads:

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Database connection; store in the provider's secrets |
| `CARDWISE_CATALOGUE_MODE` | `REAL` for public use (default is `DEMO`) |
| `PORT` | HTTP port (default 8080) |
| `SPRING_FLYWAY_TARGET` | Optional migration gate; `11` holds back V12 |

Facts about the repository that affect deployment:

- `backend/Dockerfile` builds a container image (Java 25) and exposes 8080.
- The frontend calls relative `/api/...` URLs. There is no API base URL setting
  and no CORS configuration, so the frontend and backend must be served from the
  same origin (a reverse proxy or rewrite for `/api` is required).
- `spring.jpa.show-sql=true` is enabled and will be verbose in production.
- Only the `health` actuator endpoint is exposed.
- No hosting configuration (`render.yaml`, `vercel.json`, CI workflows) exists
  yet. The target platform is still to be decided.

If more than one backend instance shares the database, Flyway uses a database
lock so only one instance migrates at a time; the others wait. All instances must
still run builds that understand V10 to V12 before activation (hazard 3).

## 7. Diagnosing a failed V12

The message begins `V12 activation blocked:` and names the failing check:
one card, zero fee, policy (2/3, 90 days, savings-account requirement), 8 rules,
4 selectable rules at 2% / 0.5%, 4 fixed rules at 0.5%, or the single-row update.
Inspect `credit_cards`, `card_selection_policies` and `reward_rules` for the
Tangerine rows, fix the data through a reviewed change, and retry. Never edit V12.

## 8. Pre-push checklist

- [ ] The database password that appeared in a terminal command has been removed
      from shell history and rotated if the database is shared or remote.
- [ ] No secrets or connection strings are committed (`git grep -nEi "password|jdbc:"`
      shows only property placeholders and test configuration).
- [ ] This runbook contains no credentials.
- [ ] The hosting platforms for backend, frontend and database are chosen, and
      the same-origin `/api` routing is in place.
- [ ] A backup exists and has been restore-tested.
- [ ] The person running migrations and the person authorized for emergency SQL
      are named.
- [ ] No pre-selection-aware instance can still serve the shared database.
- [ ] `git diff --check` is clean and the working tree contains only intended
      commits.
