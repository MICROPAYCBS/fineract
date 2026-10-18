# Micropay database migrations

Micropay migrations live in `custom/micropay/platform/db/src/main/resources/db/custom-changelog/`, one file per change, named `micropay_<nnnn>_<name>.xml`. Take the next free number after the highest existing `micropay_<nnnn>_` file. Workflow schema lives in `custom/micropay/workflow/starter/src/main/resources/db/custom-changelog/` as `micropay_workflow_<nnnn>_<name>.xml`.

`fineract-provider/src/main/resources/db/changelog/db.changelog-master.xml` loads every file in `db/custom-changelog` with `includeAll` (context `tenant_db AND !initial_switch AND custom_changelog`). Do not add an include, and do not recreate `tenant/module/micropay/module-changelog-master.xml`.

Never add Micropay files to the core tenant changelog `tenant/parts/` or to `tenant/changelog-tenant.xml`. Those numbers belong to Apache Fineract. Reusing them makes every upstream merge conflict, and the same number then means a different change in each repository. Never edit an upstream changelog in place; add a new changeset that applies the change (for example an `UPDATE`).

Set `logicalFilePath` on every file to `db/changelog/tenant/module/micropay/parts/<nnnn>_<name>.xml` (workflow: `db/changelog/tenant/module/workflow/parts/<nnnn>_<name>.xml`). Liquibase records that path, so moving the file later does not re-run the changeset. Do not change the changeset id, author, or `logicalFilePath` of a changeset that has already run.

`micropay_3000_01` through `micropay_3000_15` are the changesets that used to be core `0241` through `0255`. Their `logicalFilePath` values stay on the old `db/changelog/tenant/parts/` paths so databases that already applied them do not run them again.
