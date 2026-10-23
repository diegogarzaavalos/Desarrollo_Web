-- V1 ya está aplicada. Un CHECK nuevo no recrea alumnos ni borra filas.
-- NOT NULL deja pasar '' y espacios; el dominio Java rechaza ambos con isBlank.
ALTER TABLE alumnos
    ADD CONSTRAINT ck_nombre_no_vacio CHECK (LENGTH(TRIM(nombre)) > 0);
