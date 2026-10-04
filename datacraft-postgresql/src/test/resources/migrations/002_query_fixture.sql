-- Disposable integration-test database only; never an existing user database.
CREATE TABLE "craft_%".query_guard (id integer);
CREATE FUNCTION "craft_%".write_from_select() RETURNS integer LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO "craft_%".query_guard VALUES (1);
    RETURN 1;
END;
$$;
