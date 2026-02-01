-- ============================================================================
-- Migration: Setup PostgreSQL Extensions
-- Date: 2026-01-07
-- Description: Enable required PostgreSQL extensions
-- ============================================================================

-- Enable UUID generation functions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Enable encryption functions (for future use)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Enable trigram matching for full-text search
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
