-- Add username column for Spring Security UserDetails (login identity).
-- Idempotent: only adds column if it does not exist (e.g. DB was created with older schema).
DROP PROCEDURE IF EXISTS add_username_column_if_missing;

DELIMITER //
CREATE PROCEDURE add_username_column_if_missing()
BEGIN
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'username') = 0 THEN
        ALTER TABLE users ADD COLUMN username VARCHAR(255) NULL AFTER email;
        UPDATE users SET username = email WHERE username IS NULL;
        ALTER TABLE users MODIFY COLUMN username VARCHAR(255) NOT NULL;
    END IF;
END //
DELIMITER ;

CALL add_username_column_if_missing();
DROP PROCEDURE add_username_column_if_missing;
