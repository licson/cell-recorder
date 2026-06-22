## ADDED Requirements

### Requirement: Database upgrade path coverage from all released schema versions

The system SHALL support upgrade paths from every previously-released Room schema version to the current `@Database(version = N)` version via registered `Migration` objects in `DatabaseModule.addMigrations(...)`. A user on any released schema version MUST be able to upgrade to the current version without data loss and without an `IllegalStateException: A migration from X to Y was required but not found`.

#### Scenario: User upgrades from earliest released schema version

- **GIVEN** a user is running an app build whose database is on the earliest released schema version (v1)
- **WHEN** the user upgrades to a build whose `@Database(version = N)` is the current version
- **THEN** the database MUST migrate cleanly from v1 to vN through the chain of registered `Migration` objects
- **AND** the user's existing data MUST be preserved (no destructive fallback)
- **AND** the app MUST NOT crash with `IllegalStateException: A migration from X to Y was required but not found`

#### Scenario: Every version step has a registered migration

- **WHEN** the app is built with `@Database(version = N)` and `exportSchema = true`
- **AND** schema JSONs exist in `app/schemas/com.cellrecorder.app.data.local.AppDatabase/` for versions 1 through N
- **THEN** `DatabaseModule.addMigrations(...)` MUST include a `Migration` object for each step `i → i+1` for `i` from 1 to `N-1`
- **AND** `fallbackToDestructiveMigration()` MUST NOT be called on the `Room.databaseBuilder`

#### Scenario: Column-dropping migration uses table rebuild pattern

- **WHEN** a migration needs to drop a column from a SQLite table on API 30 (where SQLite cannot `ALTER TABLE ... DROP COLUMN` directly)
- **THEN** the migration MUST use the create-new-table / copy-data / drop-old-table / rename pattern
- **AND** the migration MUST preserve all surviving columns' data through the copy step
- **AND** a row-seeded migration test MUST verify the surviving data round-trips through the migration
