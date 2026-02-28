-- Add created_at only where missing; add modified_at to all tables.
-- created_at: set on insert via DEFAULT CURRENT_TIMESTAMP.
-- modified_at: set on insert and updated on every change via ON UPDATE CURRENT_TIMESTAMP.

-- users: already has created_at; add modified_at
ALTER TABLE users
    ADD COLUMN modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- assistant_profile: already has created_at; add modified_at
ALTER TABLE assistant_profile
    ADD COLUMN modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- tasks: already has created_at; add modified_at
ALTER TABLE tasks
    ADD COLUMN modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- people: already has created_at; add modified_at
ALTER TABLE people
    ADD COLUMN modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- chat_messages: already has created_at; add modified_at
ALTER TABLE chat_messages
    ADD COLUMN modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- reminder_log: add created_at and modified_at
ALTER TABLE reminder_log
    ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
