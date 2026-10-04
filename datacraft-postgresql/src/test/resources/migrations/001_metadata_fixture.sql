-- Applied only to a freshly provisioned Testcontainers database.
CREATE SCHEMA "craft_%";
CREATE DOMAIN "craft_%".required_text AS text NOT NULL;
CREATE TYPE "craft_%".mood AS ENUM ('quiet', 'busy');
CREATE TABLE "craft_%"."Odd' Table" (
    id integer NOT NULL,
    obsolete text,
    "Camel Column" text,
    payload jsonb,
    tags text[],
    required "craft_%".required_text,
    status "craft_%".mood
);
ALTER TABLE "craft_%"."Odd' Table" DROP COLUMN obsolete;
CREATE TABLE "craft_%".empty_table ();
CREATE VIEW "craft_%".sample_view AS SELECT id FROM "craft_%"."Odd' Table";
CREATE MATERIALIZED VIEW "craft_%".sample_materialized AS SELECT id FROM "craft_%"."Odd' Table";
CREATE TABLE "craft_%".partitioned (id integer) PARTITION BY RANGE (id);
CREATE TABLE "craft_%".partition_child PARTITION OF "craft_%".partitioned FOR VALUES FROM (0) TO (10);
