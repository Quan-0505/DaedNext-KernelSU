//! Android module Wi-Fi policy. SSIDs are data, never shell source.
use super::*;
use base64::Engine;
use serde::{Deserialize, Serialize};
use std::os::unix::fs::OpenOptionsExt;

const MAX_CONFIG_BYTES: usize = 16 * 1024;
const DEFAULT_CONFIG: &str = "/data/adb/daenext/wifi-policy.json";
const DEFAULT_STATE: &str = "/data/adb/daenext/run/wifi-policy-state.json";

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
enum ListMode {
    #[default]
    Blacklist,
    Whitelist,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(default, deny_unknown_fields)]
struct WifiConfig {
    enabled: bool,
    mode: ListMode,
    blacklist: Vec<String>,
    whitelist: Vec<String>,
    proxy_on_non_wifi: bool,
}
impl Default for WifiConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            mode: ListMode::Blacklist,
            blacklist: vec![],
            whitelist: vec![],
            proxy_on_non_wifi: true,
        }
    }
}
impl WifiConfig {
    fn validate(&mut self) -> Result<(), String> {
        for names in [&mut self.blacklist, &mut self.whitelist] {
            if names.len() > 128 {
                return Err("A Wi-Fi list supports at most 128 SSIDs".into());
            }
            if names
                .iter()
                .any(|n| n.is_empty() || n.len() > 32 || n.chars().any(char::is_control))
            {
                return Err(
                    "Each SSID must contain 1-32 UTF-8 bytes without control characters".into(),
                );
            }
            let mut seen = HashSet::new();
            names.retain(|name| seen.insert(name.clone()));
        }
        Ok(())
    }
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
struct WifiDecision {
    enabled: bool,
    mode: ListMode,
    direct: bool,
    network: String,
    interface: Option<String>,
    ssid: Option<String>,
    reason: String,
}

fn decide(config: &WifiConfig, interface: Option<String>, ssid: Option<String>) -> WifiDecision {
    let is_wifi = interface.as_deref().is_some_and(|i| i.starts_with("wlan"));
    let network = if interface.is_none() {
        "unavailable"
    } else if is_wifi {
        "wifi"
    } else {
        "non-wifi"
    };
    let ssid = if is_wifi { ssid } else { None };
    let (direct, reason) = if !config.enabled {
        (false, "disabled")
    } else if interface.is_none() {
        (false, "waiting-for-network")
    } else if !is_wifi {
        (
            !config.proxy_on_non_wifi,
            if config.proxy_on_non_wifi {
                "non-wifi-proxy"
            } else {
                "non-wifi-direct"
            },
        )
    } else if let Some(name) = ssid.as_ref() {
        match config.mode {
            ListMode::Blacklist if config.blacklist.contains(name) => (true, "blacklist-match"),
            ListMode::Blacklist => (false, "blacklist-miss"),
            ListMode::Whitelist if config.whitelist.contains(name) => (false, "whitelist-match"),
            ListMode::Whitelist => (true, "whitelist-miss"),
        }
    } else {
        (false, "ssid-unavailable")
    };
    WifiDecision {
        enabled: config.enabled,
        mode: config.mode,
        direct,
        network: network.into(),
        interface,
        ssid,
        reason: reason.into(),
    }
}

fn parse_ssid(status: &str) -> Option<String> {
    status.lines().find_map(|line| {
        let name = line
            .trim()
            .strip_prefix("Wifi is connected to \"")?
            .strip_suffix('"')?;
        (!name.is_empty() && name != "<unknown ssid>").then(|| name.to_owned())
    })
}
fn parse_interface(route: &str) -> Option<String> {
    let fields: Vec<_> = route.split_whitespace().collect();
    fields
        .windows(2)
        .find(|w| w[0] == "dev")
        .map(|w| w[1])
        .filter(|n| !matches!(*n, "lo" | "dummy0" | "dae0"))
        .map(str::to_owned)
}
fn observe(config: &WifiConfig) -> WifiDecision {
    let mut interface = None;
    for (family, destination) in [("-4", "1.1.1.1"), ("-6", "2606:4700:4700::1111")] {
        if let Ok(out) = Command::new("/system/bin/ip")
            .args([family, "route", "get", destination])
            .output()
            && out.status.success()
        {
            interface = parse_interface(&String::from_utf8_lossy(&out.stdout));
            if interface.is_some() {
                break;
            }
        }
    }
    let ssid = if config.enabled && interface.as_deref().is_some_and(|i| i.starts_with("wlan")) {
        Command::new("/system/bin/cmd")
            .args(["wifi", "status"])
            .output()
            .ok()
            .filter(|o| o.status.success())
            .and_then(|o| parse_ssid(&String::from_utf8_lossy(&o.stdout)))
    } else {
        None
    };
    decide(config, interface, ssid)
}
fn read_json<T: serde::de::DeserializeOwned>(path: &Path) -> Result<T, String> {
    let bytes = fs::read(path).map_err(|e| e.to_string())?;
    if bytes.len() > MAX_CONFIG_BYTES {
        return Err("Wi-Fi policy file exceeds 16 KiB".into());
    }
    serde_json::from_slice(&bytes).map_err(|e| e.to_string())
}
fn load_config(path: &Path) -> Result<WifiConfig, String> {
    let mut config = if path.exists() {
        read_json(path)?
    } else {
        WifiConfig::default()
    };
    config.validate()?;
    Ok(config)
}
fn write_json<T: Serialize>(path: &Path, value: &T) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }
    let tmp = path.with_extension(format!("tmp-{}", std::process::id()));
    let result = (|| {
        let mut file = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .mode(0o600)
            .open(&tmp)
            .map_err(|e| e.to_string())?;
        serde_json::to_writer(&mut file, value).map_err(|e| e.to_string())?;
        file.write_all(b"\n").map_err(|e| e.to_string())?;
        file.sync_all().map_err(|e| e.to_string())?;
        fs::rename(&tmp, path).map_err(|e| e.to_string())
    })();
    if result.is_err() {
        let _ = fs::remove_file(&tmp);
    }
    result
}

pub(super) fn initial_direct_requested() -> bool {
    std::env::var_os("DAE_NETWORK_POLICY_STATE")
        .and_then(|p| read_json::<WifiDecision>(Path::new(&p)).ok())
        .is_some_and(|d| d.enabled && d.direct)
}

#[derive(Serialize, Deserialize)]
struct Observation {
    decision: WifiDecision,
    count: u32,
}

pub(super) fn run_command(args: &[String]) -> DaedProductOutput {
    match run(args) {
        Ok(value) => DaedProductOutput::ok(format!("{value}\n")),
        Err(error) => DaedProductOutput::error(error),
    }
}
fn run(args: &[String]) -> Result<Value, String> {
    let action = args.first().map(String::as_str).unwrap_or("get");
    let mut config_path = PathBuf::from(DEFAULT_CONFIG);
    let mut state_path = PathBuf::from(DEFAULT_STATE);
    let mut control = PathBuf::from(DEFAULT_CONTROL_SOCKET);
    let mut data = None;
    let mut index = 1;
    while index < args.len() {
        let key = &args[index];
        let value = args.get(index + 1).ok_or("Missing Wi-Fi policy argument")?;
        match key.as_str() {
            "--config" => config_path = value.into(),
            "--state" => state_path = value.into(),
            "--control" => control = value.into(),
            "--data" => data = Some(value.as_str()),
            _ => return Err(format!("Unknown Wi-Fi policy argument: {key}")),
        }
        index += 2;
    }
    if action == "set" {
        if unsafe { libc::geteuid() } != 0 {
            return Err("Root is required".into());
        }
        let bytes = base64::engine::general_purpose::STANDARD
            .decode(data.ok_or("Missing base64 configuration")?)
            .map_err(|_| "Invalid base64 configuration")?;
        if bytes.len() > MAX_CONFIG_BYTES {
            return Err("Wi-Fi configuration exceeds 16 KiB".into());
        }
        let mut config: WifiConfig = serde_json::from_slice(&bytes).map_err(|e| e.to_string())?;
        config.validate()?;
        write_json(&config_path, &config)?;
        return Ok(json!({"ok":true,"config":config}));
    }
    let config = load_config(&config_path)?;
    let previous = read_json::<WifiDecision>(&state_path).ok();
    if action == "get" {
        let mut observing = config.clone();
        observing.enabled = true;
        let current = observe(&observing);
        return Ok(json!({"config":config,"status":previous,"current":current}));
    }
    if action == "status" {
        return Ok(serde_json::to_value(previous).map_err(|e| e.to_string())?);
    }
    if !matches!(action, "init" | "sync") {
        return Err("Usage: daed wifi-policy {get|set|status|init|sync} [--config PATH --state PATH --control PATH --data BASE64]".into());
    }
    if unsafe { libc::geteuid() } != 0 {
        return Err("Root is required".into());
    }
    let decision = observe(&config);
    if action == "init" {
        write_json(&state_path, &decision)?;
        return Ok(json!({"ok":true,"decision":decision}));
    }
    // Two consecutive observations avoid reacting to a stale SSID during a
    // default-route transition. An unchanged decision remains idempotent.
    let observation_path = state_path.with_extension("observation.json");
    let old: Option<Observation> = read_json(&observation_path).ok();
    let count = old
        .as_ref()
        .filter(|o| o.decision == decision)
        .map_or(1, |o| o.count.saturating_add(1).min(2));
    if old
        .as_ref()
        .is_none_or(|o| o.decision != decision || o.count != count)
    {
        write_json(
            &observation_path,
            &Observation {
                decision: decision.clone(),
                count,
            },
        )?;
    }
    if count < 2 && previous.as_ref() != Some(&decision) {
        return Ok(json!({"ok":true,"pending":true}));
    }
    let response =
        dae_product_control::request_local_control_network_policy(&control, decision.direct)
            .map_err(|e| e.to_string())?;
    if response["ok"] != true {
        return Err(response["error"]
            .as_str()
            .unwrap_or("Wi-Fi policy apply failed")
            .into());
    }
    if previous.as_ref() != Some(&decision) {
        write_json(&state_path, &decision)?;
    }
    Ok(json!({"ok":true,"decision":decision,"runtime":response}))
}

#[cfg(test)]
mod tests {
    use super::*;
    fn config(mode: ListMode) -> WifiConfig {
        WifiConfig {
            enabled: true,
            mode,
            blacklist: vec!["Managed WiFi".into()],
            whitelist: vec!["Ordinary WiFi".into()],
            ..Default::default()
        }
    }
    fn wifi(c: &WifiConfig, ssid: &str) -> WifiDecision {
        decide(c, Some("wlan0".into()), Some(ssid.into()))
    }
    #[test]
    fn wifi_blacklist_bypasses_only_matching_wifi_and_recovers_on_cellular() {
        let c = config(ListMode::Blacklist);
        assert!(wifi(&c, "Managed WiFi").direct);
        assert!(!wifi(&c, "Ordinary WiFi").direct);
        assert!(!decide(&c, Some("rmnet_data4".into()), Some("Managed WiFi".into())).direct);
    }
    #[test]
    fn wifi_whitelist_proxies_only_matching_wifi_with_independent_cellular_setting() {
        let mut c = config(ListMode::Whitelist);
        assert!(!wifi(&c, "Ordinary WiFi").direct);
        assert!(wifi(&c, "Managed WiFi").direct);
        assert!(!decide(&c, Some("rmnet_data4".into()), None).direct);
        c.proxy_on_non_wifi = false;
        assert!(decide(&c, Some("rmnet_data4".into()), None).direct);
    }
    #[test]
    fn wifi_unknown_ssid_and_route_gaps_do_not_create_unintended_bypass() {
        for mode in [ListMode::Blacklist, ListMode::Whitelist] {
            let c = config(mode);
            assert!(!decide(&c, Some("wlan0".into()), None).direct);
            assert!(!decide(&c, None, Some("Managed WiFi".into())).direct);
        }
        assert!(!wifi(&WifiConfig::default(), "Managed WiFi").direct);
    }
    #[test]
    fn wifi_ssids_match_exactly_and_preserve_unicode_spaces_quotes_and_punctuation() {
        let name = " 家里,\"WiFi\" ";
        assert_eq!(
            parse_ssid(&format!("Wifi is connected to \"{name}\"\n")),
            Some(name.into())
        );
        let mut c = config(ListMode::Blacklist);
        c.blacklist = vec![name.into()];
        assert!(wifi(&c, name).direct);
        assert!(!wifi(&c, name.trim()).direct);
        assert!(!wifi(&c, " 家里,\"wifi\" ").direct);
        assert_eq!(parse_ssid("Wifi is connected to \"<unknown ssid>\""), None);
    }
    #[test]
    fn wifi_configuration_validation_rejects_injection_delimiters_and_preserves_duplicates_safely()
    {
        let mut c = config(ListMode::Blacklist);
        c.blacklist.push("Managed WiFi".into());
        c.validate().unwrap();
        assert_eq!(c.blacklist.len(), 1);
        c.blacklist.push("bad\nssid".into());
        assert!(c.validate().is_err());
        assert!(serde_json::from_str::<WifiConfig>(r#"{"mode":"invalid"}"#).is_err());
        assert!(
            serde_json::from_str::<WifiConfig>(r#"{"enabled":true,"shell":"unexpected"}"#).is_err()
        );
    }
    #[test]
    fn wifi_detection_uses_active_default_route_instead_of_connected_ssid_alone() {
        let c = config(ListMode::Blacklist);
        let iface = parse_interface("1.1.1.1 via 192.0.2.1 dev rmnet_data2 table rmnet_data2");
        assert!(!decide(&c, iface, Some("Managed WiFi".into())).direct);
        assert_eq!(parse_interface("default dev dummy0"), None);
    }
}
