DELIMITER //
CREATE TRIGGER protect_superadmin_delete
BEFORE DELETE ON users FOR EACH ROW
BEGIN
    IF OLD.is_superadmin = 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Super Admin cannot be deleted.';
    END IF;
END//

CREATE TRIGGER protect_superadmin_update
BEFORE UPDATE ON users FOR EACH ROW
BEGIN
    IF OLD.is_superadmin = 1 
        AND (NEW.is_superadmin IS NULL OR NEW.is_superadmin = 0 OR NEW.is_admin = 0 OR NEW.is_suspended = 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Super Admin cannot be suspended or demoted.';
    END IF;
END//

CREATE TRIGGER log_user_changes
AFTER UPDATE ON users FOR EACH ROW
BEGIN
    IF NOT (OLD.is_admin <=> NEW.is_admin) THEN
        INSERT INTO log_user_changes (target_user_id, actor_user_id, action)
        VALUES (NEW.user_id, @actor_user_id, 
            IF(NEW.is_admin = 1, 'ADMIN ADDED', 'ADMIN REMOVED'));
    END IF;

    IF NOT (OLD.is_suspended <=> NEW.is_suspended) THEN
        INSERT INTO log_user_changes (target_user_id, actor_user_id, action)
        VALUES (NEW.user_id, @actor_user_id, 
            IF(NEW.is_suspended = 1, 'USER SUSPENDED', 'USER UNSUSPENDED'));
    END IF;
END//

CREATE TRIGGER log_incident_changes
AFTER DELETE ON incidents FOR EACH ROW
BEGIN
    INSERT INTO log_incident_changes (incident_id, actor_user_id, action)
        VALUES (OLD.incident_id, @actor_user_id, 'INCIDENT REMOVED');
END//
DELIMITER ;