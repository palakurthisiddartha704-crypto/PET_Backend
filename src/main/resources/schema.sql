-- =============================================================================
-- Database Schema: Personal Expense Tracker
-- RDBMS: MySQL 8.x
-- Monetary Precision: DECIMAL(12, 2)
-- Strict Isolation by user_id on all tables
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `expense_tracker_db` 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE `expense_tracker_db`;

-- -----------------------------------------------------------------------------
-- Table: users
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `users` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `email` VARCHAR(150) NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `full_name` VARCHAR(100) NOT NULL,
  `currency` VARCHAR(10) NOT NULL DEFAULT 'USD',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- Table: savings_entries (one user's overall savings ledger)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `savings_entries` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `entry_type` ENUM('MANUAL_DEPOSIT', 'MONTHLY_ROLLOVER', 'WITHDRAWAL') NOT NULL,
  `amount` DECIMAL(12, 2) NOT NULL,
  `period_year` SMALLINT DEFAULT NULL,
  `period_month` TINYINT DEFAULT NULL,
  `rollover_key` VARCHAR(32) DEFAULT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_savings_rollover_key` (`user_id`, `rollover_key`),
  KEY `idx_savings_user_created` (`user_id`, `created_at`),
  CONSTRAINT `fk_savings_entries_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Table: categories
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `categories` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `name` VARCHAR(80) NOT NULL,
  `type` ENUM('INCOME', 'EXPENSE') NOT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `display_color` VARCHAR(20) NOT NULL DEFAULT '#3b82f6',
  `icon` VARCHAR(50) DEFAULT 'Tag',
  `budget_group` ENUM('NEEDS', 'WANTS') DEFAULT NULL,
  `is_default` BOOLEAN NOT NULL DEFAULT FALSE,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_category_type` (`user_id`, `name`, `type`),
  KEY `idx_categories_user_id` (`user_id`),
  CONSTRAINT `fk_categories_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Table: transactions
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `transactions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `category_id` BIGINT NOT NULL,
  `type` ENUM('INCOME', 'EXPENSE') NOT NULL,
  `amount` DECIMAL(12, 2) NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `transaction_date` DATE NOT NULL,
  `payment_method` VARCHAR(50) NOT NULL DEFAULT 'Card',
  `notes` TEXT DEFAULT NULL,
  `recurring_rule_id` BIGINT DEFAULT NULL,
  `recurring_occurrence_date` DATE DEFAULT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_transactions_user_date` (`user_id`, `transaction_date`),
  KEY `idx_transactions_category_id` (`category_id`),
  KEY `idx_transactions_user_type` (`user_id`, `type`),
  UNIQUE KEY `uk_recurring_occurrence` (`recurring_rule_id`, `recurring_occurrence_date`),
  CONSTRAINT `fk_transactions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_transactions_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Table: budgets
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `budgets` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `category_id` BIGINT NOT NULL,
  `monthly_limit` DECIMAL(12, 2) NOT NULL,
  `month` TINYINT NOT NULL,
  `year` SMALLINT NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_category_month_year` (`user_id`, `category_id`, `month`, `year`),
  KEY `idx_budgets_user_period` (`user_id`, `year`, `month`),
  CONSTRAINT `fk_budgets_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_budgets_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Table: recurring_transactions
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `recurring_transactions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `category_id` BIGINT NOT NULL,
  `type` ENUM('INCOME', 'EXPENSE') NOT NULL,
  `amount` DECIMAL(12, 2) NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `frequency` ENUM('WEEKLY', 'MONTHLY', 'YEARLY') NOT NULL,
  `start_date` DATE NOT NULL,
  `end_date` DATE DEFAULT NULL,
  `next_execution_date` DATE NOT NULL,
  `active` BOOLEAN NOT NULL DEFAULT TRUE,
  `is_subscription` BOOLEAN NOT NULL DEFAULT FALSE,
  `last_executed_at` DATE DEFAULT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_recurring_user_active` (`user_id`, `active`),
  KEY `idx_recurring_execution_date` (`active`, `next_execution_date`),
  CONSTRAINT `fk_recurring_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_recurring_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- Backward-compatible upgrades
-- =============================================================================
-- CREATE TABLE IF NOT EXISTS defines a clean install. These conditional ALTERs
-- also bring older installations forward without dropping existing data.

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'categories' AND COLUMN_NAME = 'budget_group') = 0,
  'ALTER TABLE `categories` ADD COLUMN `budget_group` ENUM(''NEEDS'', ''WANTS'') DEFAULT NULL AFTER `icon`',
  'SELECT 1'
);
PREPARE schema_stmt FROM @ddl;
EXECUTE schema_stmt;
DEALLOCATE PREPARE schema_stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recurring_transactions' AND COLUMN_NAME = 'is_subscription') = 0,
  'ALTER TABLE `recurring_transactions` ADD COLUMN `is_subscription` BOOLEAN NOT NULL DEFAULT FALSE AFTER `active`',
  'SELECT 1'
);
PREPARE schema_stmt FROM @ddl;
EXECUTE schema_stmt;
DEALLOCATE PREPARE schema_stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'transactions' AND COLUMN_NAME = 'recurring_rule_id') = 0,
  'ALTER TABLE `transactions` ADD COLUMN `recurring_rule_id` BIGINT DEFAULT NULL AFTER `notes`',
  'SELECT 1'
);
PREPARE schema_stmt FROM @ddl;
EXECUTE schema_stmt;
DEALLOCATE PREPARE schema_stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'transactions' AND COLUMN_NAME = 'recurring_occurrence_date') = 0,
  'ALTER TABLE `transactions` ADD COLUMN `recurring_occurrence_date` DATE DEFAULT NULL AFTER `recurring_rule_id`',
  'SELECT 1'
);
PREPARE schema_stmt FROM @ddl;
EXECUTE schema_stmt;
DEALLOCATE PREPARE schema_stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'transactions' AND INDEX_NAME = 'uk_recurring_occurrence') = 0,
  'ALTER TABLE `transactions` ADD UNIQUE KEY `uk_recurring_occurrence` (`recurring_rule_id`, `recurring_occurrence_date`)',
  'SELECT 1'
);
PREPARE schema_stmt FROM @ddl;
EXECUTE schema_stmt;
DEALLOCATE PREPARE schema_stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
   WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'transactions'
     AND CONSTRAINT_NAME = 'fk_transactions_recurring_rule' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
  'ALTER TABLE `transactions` ADD CONSTRAINT `fk_transactions_recurring_rule` FOREIGN KEY (`recurring_rule_id`) REFERENCES `recurring_transactions` (`id`) ON DELETE SET NULL',
  'SELECT 1'
);
PREPARE schema_stmt FROM @ddl;
EXECUTE schema_stmt;
DEALLOCATE PREPARE schema_stmt;

-- Seed suggested 50/30/20 groups for common existing expense categories.
-- Existing user-selected groups are preserved; other categories remain NULL
-- until each user classifies them in the app.
UPDATE `categories`
SET `budget_group` = CASE
  WHEN `name` IN ('Food & Dining', 'Transportation', 'Rent & Housing', 'Utilities', 'Healthcare', 'Education') THEN 'NEEDS'
  WHEN `name` IN ('Shopping', 'Entertainment', 'Travel') THEN 'WANTS'
  ELSE NULL
END
WHERE `type` = 'EXPENSE' AND `budget_group` IS NULL;
