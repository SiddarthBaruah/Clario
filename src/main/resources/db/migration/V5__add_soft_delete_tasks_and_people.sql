-- Soft delete for tasks: never physically delete; mark is_deleted = true.
ALTER TABLE tasks ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_tasks_user_id_not_deleted ON tasks(user_id, is_deleted);

-- Soft delete for people: same pattern.
ALTER TABLE people ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_people_user_id_not_deleted ON people(user_id, is_deleted);
