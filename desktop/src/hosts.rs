// Manages the Windows hosts file.
// Writes/removes a marked block between MARKER_START and MARKER_END.

use anyhow::{Context, Result};
use std::path::{Path, PathBuf};

const MARKER_START: &str = "# === blockcorn-start ===";
const MARKER_END: &str = "# === blockcorn-end ===";

#[cfg(windows)]
const HOSTS_PATH: &str = r"C:\Windows\System32\drivers\etc\hosts";
#[cfg(not(windows))]
const HOSTS_PATH: &str = "/etc/hosts";

pub fn hosts_path() -> PathBuf {
    Path::new(HOSTS_PATH).to_owned()
}

/// Read current hosts content, stripping any previous BlockCorn section.
fn read_without_blockcorn(path: &Path) -> Result<String> {
    let content = std::fs::read_to_string(path)
        .with_context(|| format!("Failed to read {}", path.display()))?;

    let mut out = String::with_capacity(content.len());
    let mut inside = false;

    for line in content.lines() {
        if line.trim() == MARKER_START {
            inside = true;
            continue;
        }
        if line.trim() == MARKER_END {
            inside = false;
            continue;
        }
        if !inside {
            out.push_str(line);
            out.push('\n');
        }
    }

    Ok(out)
}

/// Returns true if the hosts file currently contains the BlockCorn section.
pub fn is_active(path: &Path) -> Result<bool> {
    let content = std::fs::read_to_string(path)
        .with_context(|| format!("Failed to read {}", path.display()))?;
    Ok(content.contains(MARKER_START))
}

/// Inject domains into the hosts file (creates section if absent).
pub fn apply_blocklist(path: &Path, domains: &[String]) -> Result<()> {
    let base = read_without_blockcorn(path)?;

    let mut section = String::from(MARKER_START);
    section.push('\n');
    for domain in domains {
        section.push_str(&format!("0.0.0.0 {domain}\n"));
        section.push_str(&format!("0.0.0.0 www.{domain}\n"));
    }
    section.push_str(MARKER_END);
    section.push('\n');

    let final_content = format!("{base}\n{section}");
    std::fs::write(path, final_content)
        .with_context(|| format!("Failed to write {}", path.display()))?;

    Ok(())
}

/// Remove the BlockCorn section from the hosts file.
pub fn remove_blocklist(path: &Path) -> Result<()> {
    let cleaned = read_without_blockcorn(path)?;
    std::fs::write(path, cleaned)
        .with_context(|| format!("Failed to write {}", path.display()))?;
    Ok(())
}

/// Self-healing: if the section disappeared but blocking should be active, re-inject.
pub fn ensure_active(path: &Path, domains: &[String]) -> Result<bool> {
    if !is_active(path)? {
        apply_blocklist(path, domains)?;
        return Ok(true); // healed
    }
    Ok(false)
}
