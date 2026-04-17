// 24-hour accountability delay for disabling the filter.
//
// State is stored in two places so that reinstalling the app cannot bypass it:
//   1. Windows registry: HKLM\SOFTWARE\BlockCorn\DisableRequestedAt (primary)
//   2. %ProgramData%\BlockCorn\state.json (secondary / cross-check)
//
// Both must be absent for the timer to be considered cleared.

use anyhow::Result;
use chrono::{DateTime, Duration, Utc};
use serde::{Deserialize, Serialize};

const DELAY_HOURS: i64 = 24;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DelayState {
    pub requested_at: DateTime<Utc>,
}

impl DelayState {
    pub fn seconds_remaining(&self) -> i64 {
        let deadline = self.requested_at + Duration::hours(DELAY_HOURS);
        let remaining = deadline - Utc::now();
        remaining.num_seconds().max(0)
    }

    pub fn is_expired(&self) -> bool {
        self.seconds_remaining() == 0
    }
}

// ── Storage back-end ──────────────────────────────────────────────────────────

#[cfg(windows)]
mod storage {
    use super::DelayState;
    use anyhow::Result;
    use chrono::{DateTime, Utc};
    use winreg::enums::*;
    use winreg::RegKey;

    const REG_KEY: &str = r"SOFTWARE\BlockCorn";
    const REG_VALUE: &str = "DisableRequestedAt";

    fn programdata_path() -> std::path::PathBuf {
        let base = std::env::var("ProgramData").unwrap_or_else(|_| r"C:\ProgramData".into());
        std::path::PathBuf::from(base).join("BlockCorn").join("state.json")
    }

    pub fn read() -> Result<Option<DelayState>> {
        // Try registry first
        let from_reg = (|| -> Result<Option<DateTime<Utc>>> {
            let hklm = RegKey::predef(HKEY_LOCAL_MACHINE);
            let key = hklm.open_subkey(REG_KEY)?;
            let ts: String = key.get_value(REG_VALUE)?;
            Ok(Some(ts.parse::<DateTime<Utc>>()?))
        })().ok().flatten();

        // Try file second
        let from_file = (|| -> Result<Option<DateTime<Utc>>> {
            let p = programdata_path();
            if !p.exists() { return Ok(None); }
            let s: serde_json::Value = serde_json::from_str(&std::fs::read_to_string(p)?)?;
            let ts = s["requested_at"].as_str().ok_or_else(|| anyhow::anyhow!("missing field"))?;
            Ok(Some(ts.parse::<DateTime<Utc>>()?))
        })().ok().flatten();

        // Use the earliest of the two (most conservative)
        let ts = match (from_reg, from_file) {
            (Some(a), Some(b)) => Some(a.min(b)),
            (Some(a), None) | (None, Some(a)) => Some(a),
            (None, None) => None,
        };

        Ok(ts.map(|requested_at| DelayState { requested_at }))
    }

    pub fn write(state: &DelayState) -> Result<()> {
        let ts = state.requested_at.to_rfc3339();

        // Registry
        let hklm = RegKey::predef(HKEY_LOCAL_MACHINE);
        let (key, _) = hklm.create_subkey(REG_KEY)?;
        key.set_value(REG_VALUE, &ts)?;

        // File
        let p = programdata_path();
        std::fs::create_dir_all(p.parent().unwrap())?;
        std::fs::write(&p, serde_json::to_string(state)?)?;

        Ok(())
    }

    pub fn clear() -> Result<()> {
        let hklm = RegKey::predef(HKEY_LOCAL_MACHINE);
        if let Ok(key) = hklm.open_subkey_with_flags(REG_KEY, KEY_WRITE) {
            let _ = key.delete_value(REG_VALUE);
        }

        let p = programdata_path();
        if p.exists() { std::fs::remove_file(p)?; }

        Ok(())
    }
}

#[cfg(not(windows))]
mod storage {
    use super::DelayState;
    use anyhow::Result;
    use std::path::PathBuf;

    fn path() -> PathBuf {
        dirs::data_local_dir()
            .unwrap_or_else(|| PathBuf::from("/tmp"))
            .join("blockcorn")
            .join("delay_state.json")
    }

    pub fn read() -> Result<Option<DelayState>> {
        let p = path();
        if !p.exists() { return Ok(None); }
        Ok(Some(serde_json::from_str(&std::fs::read_to_string(p)?)?))
    }

    pub fn write(state: &DelayState) -> Result<()> {
        let p = path();
        std::fs::create_dir_all(p.parent().unwrap())?;
        std::fs::write(p, serde_json::to_string(state)?)?;
        Ok(())
    }

    pub fn clear() -> Result<()> {
        let p = path();
        if p.exists() { std::fs::remove_file(p)?; }
        Ok(())
    }
}

// ── Public API ────────────────────────────────────────────────────────────────

/// Returns the current delay state if a disable was requested.
pub fn get() -> Result<Option<DelayState>> {
    storage::read()
}

/// Record a new disable-request (starts the 24h countdown).
/// Returns the state.
pub fn request_disable() -> Result<DelayState> {
    let state = DelayState { requested_at: Utc::now() };
    storage::write(&state)?;
    Ok(state)
}

/// Clear the delay state (call only after PIN verified AND timer expired).
pub fn clear() -> Result<()> {
    storage::clear()
}

/// Check if a pending disable request has completed its 24h wait.
/// Returns `Some(state)` if expired (ready to disable), `None` if still waiting or no request.
pub fn ready_to_disable() -> Result<Option<DelayState>> {
    match storage::read()? {
        Some(s) if s.is_expired() => Ok(Some(s)),
        _ => Ok(None),
    }
}
