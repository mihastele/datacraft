CREATE TABLE "Odd' Table" (id INTEGER NOT NULL PRIMARY KEY, label TEXT, computed TEXT GENERATED ALWAYS AS (coalesce(label, 'missing')) VIRTUAL);
INSERT INTO "Odd' Table"(id, label) VALUES (1, 'alpha'), (2, NULL), (3, ''), (4, 'NULL');
CREATE VIEW labels AS SELECT label FROM "Odd' Table";
CREATE TABLE dc(value TEXT);
INSERT INTO dc VALUES ('ordinary relation');
CREATE TABLE untyped(value);
