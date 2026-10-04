CREATE TABLE `Odd' Table` (id BIGINT UNSIGNED NOT NULL PRIMARY KEY, `Camel Column` VARCHAR(50), payload JSON, computed INT GENERATED ALWAYS AS (id + 1) STORED);
INSERT INTO `Odd' Table` (id, `Camel Column`, payload) VALUES (1, 'alpha', '{"key":"value"}'), (2, NULL, NULL), (3, '', NULL), (4, 'NULL', NULL);
CREATE VIEW sample_view AS SELECT id, `Camel Column` FROM `Odd' Table`;
CREATE TABLE dc(value TEXT);
INSERT INTO dc VALUES ('ordinary relation');
CREATE TABLE query_guard(value INT);
CREATE DATABASE `craft_%`;
GRANT ALL PRIVILEGES ON `craft_%`.* TO 'dc_reader'@'%';
