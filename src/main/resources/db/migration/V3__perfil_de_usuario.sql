-- V3: perfil propio de cada usuario (foto y tema personal). El nombre ya existia (users.name).
-- El tema personal solo afecta a lo que ve ese usuario; tiene prioridad sobre el del grupo.
ALTER TABLE users ADD COLUMN theme text;
ALTER TABLE users ADD COLUMN avatar_media_id varchar(36);
ALTER TABLE users ADD CONSTRAINT fk_user_avatar FOREIGN KEY (avatar_media_id) REFERENCES media_files (id) ON DELETE SET NULL;
