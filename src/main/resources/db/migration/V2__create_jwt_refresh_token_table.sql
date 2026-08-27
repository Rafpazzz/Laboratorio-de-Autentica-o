CREATE TABLE jwt_refresh_token (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    family_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,

    CONSTRAINT uk_jwt_refresh_token_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_jwt_refresh_token_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuario (id)
        ON DELETE CASCADE,

    CONSTRAINT ck_jwt_refresh_token_expiration
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_jwt_refresh_token_usuario
    ON jwt_refresh_token (usuario_id);

CREATE INDEX idx_jwt_refresh_token_family
    ON jwt_refresh_token (family_id);

CREATE INDEX idx_jwt_refresh_token_expiration
    ON jwt_refresh_token (expires_at);
