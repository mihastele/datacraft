-- Fresh Testcontainers database only. These rows exist solely to verify the UI workflow.
CREATE SCHEMA mvp_test;
CREATE TABLE mvp_test.widgets (id integer NOT NULL, label text);
INSERT INTO mvp_test.widgets VALUES (1, 'alpha'), (2, NULL);
