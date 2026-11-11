DO $$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'valarcos') THEN
            CREATE USER valarcos WITH PASSWORD 'dev_password_123' CREATEDB NOCREATEROLE;
        END IF;
    END $$;

SELECT 'CREATE DATABASE centralizesys_test OWNER valarcos'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'centralizesys_test')\gexec

GRANT ALL PRIVILEGES ON DATABASE centralizesys TO valarcos;
GRANT ALL PRIVILEGES ON DATABASE centralizesys_test TO valarcos;

