# Micropay database migrations

Micropay migrations go only in `fineract-provider/src/main/resources/db/changelog/tenant/module/micropay/parts/` and are numbered `3xxx`. Include each new file from `tenant/module/micropay/module-changelog-master.xml`.

Never add Micropay files to the core tenant changelog `tenant/parts/` or to `tenant/changelog-tenant.xml`. Those numbers belong to Apache Fineract. Reusing them makes every upstream merge conflict, and the same number then means a different change in each repository.

`3000_01` through `3000_15` are the changesets that used to be core `0241` through `0255`. Their `logicalFilePath` values stay on the old paths so databases that already applied them do not run them again. Do not change those changeset ids, authors, or `logicalFilePath` values.
