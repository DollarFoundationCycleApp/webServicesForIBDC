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
DELIMITER ;