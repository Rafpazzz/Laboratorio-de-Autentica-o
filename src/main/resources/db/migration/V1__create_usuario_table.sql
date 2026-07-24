CREATE TABLE usuario (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(180) NOT NULL,
    password VARCHAR(255) NOT NULL,
    age INTEGER NOT NULL,
    role VARCHAR(30) NOT NULL,
    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT ck_usuario_age_not_negative CHECK (age >= 0),
    CONSTRAINT ck_usuario_role CHECK (role IN ('USER', 'ADMIN'))
);
