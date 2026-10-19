-- La cuenta se solicita sin contraseña: la crea la propia persona con un enlace cuando la federación la aprueba.
ALTER TABLE app_user MODIFY password_hash VARCHAR(100) NULL;

-- "Mantener sesión iniciada": si es TRUE la cookie del refresh token dura 30 días; si no, es una cookie de
-- sesión (se borra al cerrar el navegador) y el token caduca antes en el servidor. Se conserva al rotar.
ALTER TABLE refresh_token ADD COLUMN persistent BOOLEAN NOT NULL DEFAULT TRUE;
