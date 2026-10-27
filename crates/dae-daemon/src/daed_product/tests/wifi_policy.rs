use super::support::FreshProductState;
use super::*;

fn apply(runtime: &ProductRuntimeManager, fixture: &FreshProductState) -> Value {
    let prepared = prepare_runtime_reload_config(fixture.state()).unwrap();
    apply_prepared_runtime_reload(
        runtime,
        fixture.state(),
        Some(fixture.root()),
        "wifi-policy-test",
        prepared,
        &[],
    )
    .unwrap()
    .runtime_report
}

#[test]
fn wifi_policy_pause_keeps_user_running_intent_and_resume_restores_same_configuration() {
    with_product_runtime_fake_start_override(true, || {
        let fixture = FreshProductState::new("wifi-pause-resume");
        fixture.seed_selected_resources();
        let runtime = ProductRuntimeManager::new();
        apply(&runtime, &fixture);
        let global: String = fixture
            .connection()
            .query_row("SELECT global FROM configs WHERE selected=1", [], |r| {
                r.get(0)
            })
            .unwrap();
        runtime.set_network_policy_paused(true).unwrap();
        assert!(!runtime.is_running());
        assert!(runtime.network_policy_bypassed());
        assert!(runtime.runtime_required_for_readiness());
        assert!(should_restore_runtime_on_start(fixture.state()).unwrap());
        assert_eq!(
            apply(&runtime, &fixture)["transition"],
            "NetworkPolicyBypass"
        );
        assert!(!runtime.is_running());
        runtime.set_network_policy_paused(false).unwrap();
        apply(&runtime, &fixture);
        assert!(runtime.is_running());
        assert!(!runtime.network_policy_paused());
        assert_eq!(
            fixture
                .connection()
                .query_row("SELECT global FROM configs WHERE selected=1", [], |r| {
                    r.get::<_, String>(0)
                })
                .unwrap(),
            global
        );
    });
}

#[test]
fn wifi_policy_manual_stop_in_bypass_prevents_automatic_resume() {
    with_product_runtime_fake_start_override(true, || {
        let fixture = FreshProductState::new("wifi-manual-stop");
        fixture.seed_selected_resources();
        let runtime = ProductRuntimeManager::new();
        apply(&runtime, &fixture);
        runtime.set_network_policy_paused(true).unwrap();
        stop_runtime_and_persist(fixture.state(), &runtime).unwrap();
        runtime.set_network_policy_paused(false).unwrap();
        assert!(!runtime.is_running());
        assert!(!runtime.runtime_required_for_readiness());
        assert!(!should_restore_runtime_on_start(fixture.state()).unwrap());
    });
}

#[test]
fn wifi_policy_manual_start_while_bypassed_saves_intent_without_attaching() {
    with_product_runtime_fake_start_override(true, || {
        let fixture = FreshProductState::new("wifi-deferred-start");
        fixture.seed_selected_resources();
        let runtime = ProductRuntimeManager::new();
        runtime.set_network_policy_paused(true).unwrap();
        apply(&runtime, &fixture);
        assert!(!runtime.is_running());
        assert!(runtime.runtime_required_for_readiness());
        assert!(should_restore_runtime_on_start(fixture.state()).unwrap());
        runtime.set_network_policy_paused(false).unwrap();
        apply(&runtime, &fixture);
        assert!(runtime.is_running());
    });
}
