USE IBDCServer_db;

-- 1. Temporarily disable foreign key checks
SET FOREIGN_KEY_CHECKS = 0;

-- 2. Truncate all tables to clear data and reset auto-increment counters
TRUNCATE TABLE sessions;
TRUNCATE TABLE users;
TRUNCATE TABLE offender_vehicles;
TRUNCATE TABLE incident_photos;
TRUNCATE TABLE incidents;
TRUNCATE TABLE vehicle_models;
TRUNCATE TABLE vehicle_makes;

-- 3. Re-enable foreign key checks
SET FOREIGN_KEY_CHECKS = 1;