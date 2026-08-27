ALTER TABLE jwt_refresh_token
    ALTER COLUMN token_hash TYPE VARCHAR(64)
    USING RTRIM(token_hash)::VARCHAR(64);

ALTER TABLE jwt_refresh_token
    ADD CONSTRAINT ck_jwt_refresh_token_hash_length
        CHECK (CHAR_LENGTH(token_hash) = 64);
