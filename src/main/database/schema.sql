-- IBDC Server database schema
-- Incident information and incident images are stored in two separate tables
-- linked by incident_id. A device_event_id is only unique per user, so the
-- incident_id (server-assigned) is the reliable link between the tables.

CREATE DATABASE IF NOT EXISTS IBDCServer_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE IBDCServer_db;

-- Incident Information Table
CREATE TABLE IF NOT EXISTS incidents (
    incident_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_event_id INT UNSIGNED NOT NULL,          -- Original event_id from embedded board
    user_id VARCHAR(255) NOT NULL,                  -- ID of the reporting user
    timestamp_ms BIGINT NOT NULL,                   -- Incident timestamp (UTC)
    latitude DECIMAL(10,7),                         -- GPS coordinates from mobile app
    longitude DECIMAL(10,7),
    accuracy_meters DECIMAL(8,2),                   -- Accuracy in meters
    distance_cm INT UNSIGNED,                       -- Distance to object from embedded sensor
    time_offset_ms INT UNSIGNED,                    -- Sensor offset time in ms
    is_anonymized TINYINT(1) DEFAULT 0 NOT NULL,    -- Flag denoting sensitive info stripping
    additional_comments TEXT,                       -- Free text only admins can edit on /incident
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_device_event_id (device_event_id),
    INDEX idx_user_id (user_id),
    INDEX idx_timestamp_ms (timestamp_ms),
    UNIQUE KEY uq_device_event_user (device_event_id, user_id)
);

-- Incident Image Table (stores binary image data separately)
CREATE TABLE IF NOT EXISTS incident_photos (
    photo_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,                     -- Foreign key linking to incidents table
    image_index INT UNSIGNED NOT NULL,               -- Zero-based index within event
    format VARCHAR(50) NOT NULL DEFAULT 'JPEG',      -- e.g., "JPEG", "PNG"
    image_data MEDIUMBLOB NOT NULL,                  -- Binary image payload
    contains_sensitive_data TINYINT(1) DEFAULT 0 NOT NULL, -- Flagged if pending anonymization
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (incident_id) REFERENCES incidents(incident_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_incident_id_image_index (incident_id, image_index),
    INDEX idx_format (format),
    INDEX idx_sensitive_data (contains_sensitive_data)
);

-- Offender Vehicle Table (for repeat offender analytics)
CREATE TABLE IF NOT EXISTS offender_vehicles (
    vehicle_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT UNIQUE NOT NULL,               -- Links to incident that reported this vehicle
    license_plate VARCHAR(50),                        -- Raw or normalized license plate string
    state_or_region VARCHAR(100),                     -- Vehicle jurisdiction
    vehicle_make VARCHAR(100),                       -- Vehicle make
    vehicle_model VARCHAR(100),                      -- Vehicle model
    vehicle_color VARCHAR(100),                      -- Vehicle color
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (incident_id) REFERENCES incidents(incident_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_license_plate (license_plate),
    INDEX idx_state_or_region (state_or_region)
);

-- Vehicle Makes and Models Tables (for filtering)
CREATE TABLE IF NOT EXISTS vehicle_makes (
    make_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    make_name VARCHAR(100) UNIQUE NOT NULL        -- Vehicle make name
);

CREATE TABLE IF NOT EXISTS vehicle_models (
    model_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    make_id BIGINT NOT NULL,                      -- Foreign key linking to vehicle_makes
    model_name VARCHAR(100) NOT NULL,             -- Vehicle model name
    FOREIGN KEY (make_id) REFERENCES vehicle_makes(make_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    UNIQUE KEY uq_make_model (make_id, model_name)
);

-- User table for user authentication and incident reporting
-- Usernames and emails are stored separately: the login form accepts either.
-- Passwords are stored as SHA-256 hex hashes (see AuthenticationController).
CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(255) PRIMARY KEY,                -- User ID from mobile app or generated at sign-up
    username_or_email VARCHAR(255) UNIQUE NOT NULL,  -- Username chosen at sign-up
    email VARCHAR(255) UNIQUE NOT NULL,              -- Email address from sign-up
    password_hash VARCHAR(255) NOT NULL,             -- SHA-256 hash of the password
    is_admin TINYINT(1) DEFAULT 0 NOT NULL,          -- Set true for Admin accounts
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Upgrading an existing database: the columns above were added for the web
-- sign-up page. If the users table already exists, run these once instead of
-- re-creating it:
--   ALTER TABLE users ADD COLUMN email VARCHAR(255) NULL AFTER username_or_email;
--   ALTER TABLE users ADD COLUMN password_hash VARCHAR(255) NOT NULL DEFAULT '' AFTER email;
--   ALTER TABLE users ADD UNIQUE KEY uq_users_email (email);

-- Session tokens table for user authentication
CREATE TABLE IF NOT EXISTS sessions (
    session_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,                   -- Links to users table
    auth_token_hash VARCHAR(255) UNIQUE NOT NULL,    -- Secure hashed token
    is_admin TINYINT(1) DEFAULT 0 NOT NULL,          -- Admin flag for session holder
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,                            -- Session expiration time
    FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_auth_token_hash (auth_token_hash),
    INDEX idx_user_id (user_id)
);
