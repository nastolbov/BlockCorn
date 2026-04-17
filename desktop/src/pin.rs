// PIN management: hash stored in Windows registry under HKLM.
// Requires the process to run as Administrator for initial write.

use anyhow::{bail, Result};

const REG_KEY: &str = r"SOFTWARE\BlockCorn";
const REG_VALUE_HASH: &str = "PinHash";

// ── Storage back-end (Windows vs stub) ───────────────────────────────────────

#[cfg(windows)]
mod storage {
    use super::{REG_KEY, REG_VALUE_HASH};
    use anyhow::Result;
    use winreg::enums::*;
    use winreg::RegKey;

    pub fn read_hash() -> Result<Option<String>> {
        let hklm = RegKey::predef(HKEY_LOCAL_MACHINE);
        match hklm.open_subkey(REG_KEY) {
            Ok(key) => {
                let val: String = key.get_value(REG_VALUE_HASH)?;
                Ok(Some(val))
            }
            Err(_) => Ok(None),
        }
    }

    pub fn write_hash(hash: &str) -> Result<()> {
        let hklm = RegKey::predef(HKEY_LOCAL_MACHINE);
        let (key, _) = hklm.create_subkey(REG_KEY)?;
        key.set_value(REG_VALUE_HASH, &hash.to_string())?;
        Ok(())
    }

    pub fn delete_hash() -> Result<()> {
        let hklm = RegKey::predef(HKEY_LOCAL_MACHINE);
        if let Ok(key) = hklm.open_subkey_with_flags(REG_KEY, winreg::enums::KEY_WRITE) {
            let _ = key.delete_value(REG_VALUE_HASH);
        }
        Ok(())
    }
}

#[cfg(not(windows))]
mod storage {
    use anyhow::Result;
    use std::path::PathBuf;

    fn path() -> PathBuf {
        dirs::data_local_dir()
            .unwrap_or_else(|| PathBuf::from("/tmp"))
            .join("blockcorn")
            .join("pin.hash")
    }

    pub fn read_hash() -> Result<Option<String>> {
        let p = path();
        if !p.exists() {
            return Ok(None);
        }
        Ok(Some(std::fs::read_to_string(p)?.trim().to_owned()))
    }

    pub fn write_hash(hash: &str) -> Result<()> {
        let p = path();
        std::fs::create_dir_all(p.parent().unwrap())?;
        std::fs::write(p, hash)?;
        Ok(())
    }

    pub fn delete_hash() -> Result<()> {
        let p = path();
        if p.exists() {
            std::fs::remove_file(p)?;
        }
        Ok(())
    }
}

// ── Public API ────────────────────────────────────────────────────────────────

/// Returns true if a PIN has been configured.
pub fn is_set() -> bool {
    storage::read_hash().ok().flatten().is_some()
}

/// Hash and persist a new PIN. Replaces any existing PIN.
pub fn set_pin(pin: &str) -> Result<()> {
    if pin.len() < 4 {
        bail!("PIN must be at least 4 characters");
    }
    let hash = bcrypt::hash(pin, bcrypt::DEFAULT_COST)?;
    storage::write_hash(&hash)?;
    Ok(())
}

/// Returns true if the provided PIN matches the stored hash.
pub fn verify(pin: &str) -> Result<bool> {
    match storage::read_hash()? {
        None => bail!("No PIN is configured"),
        Some(hash) => Ok(bcrypt::verify(pin, &hash)?),
    }
}

/// Remove the stored PIN (only callable after PIN verification in the calling code).
pub fn clear_pin() -> Result<()> {
    storage::delete_hash()
}
