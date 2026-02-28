-- Add visibility column: INTERNAL (AI-only) vs USER_FACING (shown in chat UI)
ALTER TABLE chat_messages
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'USER_FACING';

-- Constrain allowed values
ALTER TABLE chat_messages
    ADD CONSTRAINT chk_chat_messages_visibility CHECK (visibility IN ('INTERNAL', 'USER_FACING'));

-- Allow TOOL role for function/tool result messages
ALTER TABLE chat_messages DROP CONSTRAINT chk_chat_messages_role;
ALTER TABLE chat_messages
    ADD CONSTRAINT chk_chat_messages_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL'));

CREATE INDEX idx_chat_messages_user_visibility_created ON chat_messages(user_id, visibility, created_at);
