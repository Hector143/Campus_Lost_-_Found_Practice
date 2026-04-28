-- =========================================================================
-- Campus Lost & Found Hub – database schema
-- Run once with:   mysql -u root -p < schema.sql
-- =========================================================================

DROP DATABASE IF EXISTS lost_and_found;
CREATE DATABASE lost_and_found CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE lost_and_found;

-- -------------------------------------------------------------------------
-- 1. users   (auth + roles)
-- -------------------------------------------------------------------------
CREATE TABLE users (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    full_name       VARCHAR(100) NOT NULL,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password_hash   VARCHAR(128) NOT NULL,         -- SHA-256 hex (64 chars)
    role            ENUM('STUDENT','ADMIN') NOT NULL DEFAULT 'STUDENT',
    avatar_url      VARCHAR(255),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- -------------------------------------------------------------------------
-- 2. items   (one row per LOST or FOUND report)
-- -------------------------------------------------------------------------
CREATE TABLE items (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    type            ENUM('LOST','FOUND') NOT NULL,
    title           VARCHAR(150) NOT NULL,
    description     TEXT,
    category        VARCHAR(50),
    location        VARCHAR(150),
    latitude        DECIMAL(10,7),                 -- filled by Nominatim API
    longitude       DECIMAL(10,7),
    reporter_id     INT NOT NULL,
    status          ENUM('OPEN','CLAIMED','RESOLVED') NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_items_reporter
        FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_items_type   (type),
    INDEX idx_items_status (status)
);

-- -------------------------------------------------------------------------
-- 3. claims  (someone says "that's mine, that's the one I found, etc.")
-- -------------------------------------------------------------------------
CREATE TABLE claims (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    item_id         INT NOT NULL,
    claimer_id      INT NOT NULL,
    message         TEXT,
    status          ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_claims_item    FOREIGN KEY (item_id)    REFERENCES items(id) ON DELETE CASCADE,
    CONSTRAINT fk_claims_claimer FOREIGN KEY (claimer_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_claims_item (item_id)
);

-- -------------------------------------------------------------------------
-- 4. activity_logs   (every interesting action gets a row here)
-- -------------------------------------------------------------------------
CREATE TABLE activity_logs (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    action          VARCHAR(100) NOT NULL,         -- e.g. LOGIN, CREATE_ITEM
    username        VARCHAR(50),                   -- null for anonymous
    details         TEXT,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_logs_action (action),
    INDEX idx_logs_user   (username)
);

-- -------------------------------------------------------------------------
-- Default admin account
-- Username: admin    Password: admin123
-- (SHA-256 of "admin123" = 240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9)
-- -------------------------------------------------------------------------
INSERT INTO users (full_name, username, password_hash, role, avatar_url) VALUES
('System Administrator',
 'admin',
 '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9',
 'ADMIN',
 'https://ui-avatars.com/api/?name=Admin&background=1B2B4A&color=fff');

-- A couple of seed items so the dashboard isn't empty on first run.
INSERT INTO items (type, title, description, category, location, reporter_id) VALUES
('LOST',  'Black Wallet',     'Brown leather, has school ID inside.', 'Personal', 'Library 2nd floor', 1),
('FOUND', 'Blue Water Bottle','Hydroflask, sticker on the back.',     'Personal', 'CCE Building lobby', 1);
