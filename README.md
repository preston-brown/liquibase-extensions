# Liquibase Extensions

## Create Audit Table

Given a base table, create a corresponding audit table. Also create a trigger and function to load data into the table.

## Update Audit Trigger

If a base table changes, you will need to change the corresponding audit table as well. Once you've done that, you can
use this extension to recreate the audit trigger and function so the match the current table definition.

## Load Update AND Delete Data

This is an extension similar to LoadUpdateData change type with the addition of functionality that deletes values from
the table that are not present in the CSV file.

## Create Timestamp Triggers 

This is an extension to add triggers to populate the `created_at` and `updated_at` columns on a database table. 