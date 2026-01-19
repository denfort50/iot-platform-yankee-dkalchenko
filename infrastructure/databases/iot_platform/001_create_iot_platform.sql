DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_database WHERE datname = 'iot_platform') THEN
       CREATE DATABASE iot_platform;
END IF;
END $$;