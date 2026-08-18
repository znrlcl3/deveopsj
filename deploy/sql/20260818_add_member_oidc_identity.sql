ALTER TABLE members
    ADD COLUMN oidc_issuer VARCHAR(255) NULL,
    ADD COLUMN oidc_subject VARCHAR(255) NULL,
    ADD CONSTRAINT uk_members_oidc_identity UNIQUE (oidc_issuer, oidc_subject);
