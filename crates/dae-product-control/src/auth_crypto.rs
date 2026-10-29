use std::io;
use std::path::Path;

use dae_product_core::unix_now;
use dae_product_identity::{
    hash_password as identity_hash_password,
    password_hash_needs_migration as identity_needs_migration,
    random_secret_hex as identity_random_secret_hex,
    secure_random_index as identity_secure_random_index, sign_hs256_token,
    validate_password_strength as identity_validate_password_strength,
    verify_password_hash as identity_verify_password_hash,
};
use dae_product_persistence::{ProductUserRecord, load_user_by_username_without_schema_check};

const TOKEN_TTL_SECONDS: u64 = 30 * 24 * 60 * 60;

/// A root-authorized local manager receives a short-lived session, never the
/// stored password or JWT signing secret. This is not exposed over HTTP.
pub fn local_manager_session(state: &Path) -> io::Result<serde_json::Value> {
    use rusqlite::OptionalExtension;
    let conn =
        rusqlite::Connection::open_with_flags(state, rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY)
            .map_err(io::Error::other)?;
    let username: Option<String> = conn
        .query_row(
            "SELECT username FROM users ORDER BY id LIMIT 1",
            [],
            |row| row.get(0),
        )
        .optional()
        .map_err(io::Error::other)?;
    let Some(username) = username else {
        return Ok(serde_json::json!({"ok":false,"setupRequired":true}));
    };
    let user = load_user_by_username_without_schema_check(state, &username)?
        .ok_or_else(|| io::Error::other("manager user no longer exists"))?;
    let expires_at = unix_now()
        .checked_add(600)
        .ok_or_else(|| io::Error::other("manager session expiration overflow"))?;
    Ok(serde_json::json!({"ok":true,"expiresAt":expires_at,
        "token":sign_hs256_token(user.username(), user.jwt_secret().as_bytes(), expires_at)}))
}

pub fn signed_token(user: &ProductUserRecord) -> io::Result<String> {
    let exp = unix_now()
        .checked_add(TOKEN_TTL_SECONDS)
        .ok_or_else(|| io::Error::other("token expiration overflow"))?;
    Ok(sign_hs256_token(
        user.username(),
        user.jwt_secret().as_bytes(),
        exp,
    ))
}

pub fn verify_token(state: &Path, token: &str) -> io::Result<Option<ProductUserRecord>> {
    let Some(parsed) = dae_product_identity::parse_hs256_token(token)? else {
        return Ok(None);
    };
    let Some(user) = load_user_by_username_without_schema_check(state, parsed.subject())? else {
        return Ok(None);
    };
    if !parsed.verify_signature(user.jwt_secret().as_bytes()) || parsed.expiration() <= unix_now() {
        return Ok(None);
    }
    Ok(Some(user))
}

pub fn hash_password(salt: &[u8], password: &str) -> String {
    identity_hash_password(salt, password)
}

pub fn verify_password_hash(stored_hash: &str, salt: &[u8], password: &str) -> bool {
    identity_verify_password_hash(stored_hash, salt, password)
}

pub fn password_hash_needs_migration(stored_hash: &str) -> bool {
    identity_needs_migration(stored_hash)
}

pub fn validate_password_strength(password: &str) -> Result<(), String> {
    identity_validate_password_strength(password)
}

pub fn random_secret_hex() -> io::Result<String> {
    identity_random_secret_hex()
}

pub fn secure_random_index<R: io::Read>(rng: &mut R, upper: usize) -> io::Result<usize> {
    identity_secure_random_index(rng, upper)
}
