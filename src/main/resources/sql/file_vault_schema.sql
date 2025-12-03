-- ============================================
-- FILE VAULT DATABASE SCHEMA
-- Unified file management for DM, Meeting, and Local files
-- ============================================

-- Main files table
CREATE TABLE IF NOT EXISTS vault_files (
    file_id TEXT PRIMARY KEY,
    
    -- Source & Identity
    source TEXT NOT NULL,  -- 'dm', 'meeting', 'local'
    source_user TEXT,      -- Username who sent (for dm/meeting)
    original_name TEXT NOT NULL,
    stored_path TEXT NOT NULL UNIQUE,
    
    -- File Properties
    file_size INTEGER NOT NULL,
    mime_type TEXT,
    
    -- Encryption
    is_encrypted BOOLEAN NOT NULL DEFAULT 0,
    encryption_key TEXT,   -- Base64 (only for local encrypted files)
    iv_base64 TEXT,        -- Initialization Vector
    
    -- Compression
    is_compressed BOOLEAN NOT NULL DEFAULT 0,
    compression_type TEXT, -- 'zip', 'gzip', NULL
    
    -- Categorization
    category_tags TEXT,    -- Comma-separated: "Documents,Work,Important"
    is_starred BOOLEAN NOT NULL DEFAULT 0,
    
    -- Metadata
    thumbnail BLOB,
    hash_sha256 TEXT,
    
    -- Timestamps
    created_at INTEGER NOT NULL,
    modified_at INTEGER NOT NULL,
    
    -- Additional metadata
    metadata_json TEXT
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_vault_files_source 
    ON vault_files(source);

CREATE INDEX IF NOT EXISTS idx_vault_files_starred 
    ON vault_files(is_starred);

CREATE INDEX IF NOT EXISTS idx_vault_files_created 
    ON vault_files(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_vault_files_category 
    ON vault_files(category_tags);

-- ============================================
-- USER CATEGORIES
-- ============================================
CREATE TABLE IF NOT EXISTS user_categories (
    category_id TEXT PRIMARY KEY,
    category_name TEXT NOT NULL UNIQUE,
    icon TEXT,             -- Icon name (e.g., 'fas-folder')
    color TEXT,            -- Hex color
    created_at INTEGER NOT NULL
);

-- Default categories
INSERT OR IGNORE INTO user_categories (category_id, category_name, icon, color, created_at) 
VALUES 
    ('cat_documents', 'Documents', 'fas-file-alt', '#3b82f6', strftime('%s', 'now') * 1000),
    ('cat_images', 'Images', 'fas-image', '#10b981', strftime('%s', 'now') * 1000),
    ('cat_archives', 'Archives', 'fas-file-archive', '#f59e0b', strftime('%s', 'now') * 1000);

-- ============================================
-- ENCRYPTED VAULT CONTAINERS
-- ============================================
CREATE TABLE IF NOT EXISTS vault_containers (
    container_id TEXT PRIMARY KEY,
    container_name TEXT NOT NULL,
    encryption_key TEXT NOT NULL,  -- Master key for container
    created_at INTEGER NOT NULL,
    file_count INTEGER DEFAULT 0
);

-- File to container mapping
CREATE TABLE IF NOT EXISTS file_to_container (
    container_id TEXT NOT NULL,
    file_id TEXT NOT NULL,
    added_at INTEGER NOT NULL,
    PRIMARY KEY (container_id, file_id),
    FOREIGN KEY (container_id) REFERENCES vault_containers(container_id) ON DELETE CASCADE,
    FOREIGN KEY (file_id) REFERENCES vault_files(file_id) ON DELETE CASCADE
);

-- ============================================
-- FILE OPERATIONS LOG
-- ============================================
CREATE TABLE IF NOT EXISTS vault_operations_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    operation_type TEXT NOT NULL,  -- 'ENCRYPT', 'DECRYPT', 'STAR', 'CATEGORY', 'DELETE'
    file_id TEXT,
    file_name TEXT,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    timestamp INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_vault_log_timestamp 
    ON vault_operations_log(timestamp DESC);

