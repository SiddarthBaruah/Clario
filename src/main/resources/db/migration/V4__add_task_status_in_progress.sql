-- Allow IN_PROGRESS as a task status (for "mark in progress" flow).
ALTER TABLE tasks DROP CONSTRAINT chk_tasks_status;
ALTER TABLE tasks ADD CONSTRAINT chk_tasks_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'DONE'));
