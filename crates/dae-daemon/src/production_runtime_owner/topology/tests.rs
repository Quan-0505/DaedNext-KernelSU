#[cfg(test)]
#[allow(clippy::module_inception)]
mod tests {
    use super::super::*;

    #[test]
    fn parses_used_netns_ids_from_ip_output() {
        let used = parse_used_netns_ids(
            "nsid 49 \nnsid 12 current-nsid unassigned \ninvalid\nnsid not-a-number\n",
        );
        assert!(used.contains(&49));
        assert!(used.contains(&12));
        assert!(!used.contains(&0));
    }

    #[test]
    fn effective_dae_netns_id_uses_latest_success_summary() {
        let steps = vec![
            json!({
                "name": "assign-production-netns-id-summary",
                "status": "pass",
                "effective_dae_netns_id": 51,
            }),
            json!({
                "name": "assign-production-netns-id-summary",
                "status": "pass",
                "effective_dae_netns_id": 52,
            }),
        ];
        assert_eq!(effective_dae_netns_id(&steps, 49), 52);
    }

    #[test]
    fn netns_id_auto_uses_newly_assigned_id() {
        let before = BTreeSet::from([49]);
        let after = BTreeSet::from([49, 50]);
        assert_eq!(new_netns_id_after_auto(&before, &after), Some(50));
    }

    #[test]
    fn explicit_netns_id_skips_namespaces_retained_by_previous_generation() {
        let before = parse_used_netns_ids("nsid 49\nnsid 50\n");
        let allocated = unused_explicit_netns_id(&before, 49).unwrap();
        assert_eq!(allocated, 51);
        let mut after = before.clone();
        after.insert(allocated);
        assert_eq!(new_netns_id_after_auto(&before, &after), Some(51));
        let steps = vec![json!({
            "name": "assign-production-netns-id-summary",
            "status": "pass",
            "effective_dae_netns_id": allocated,
        })];
        assert_eq!(effective_dae_netns_id(&steps, 49), allocated);
    }

    #[test]
    fn explicit_netns_id_keeps_free_preference_and_respects_kernel_range() {
        assert_eq!(unused_explicit_netns_id(&BTreeSet::from([12]), 49), Some(49));
        assert_eq!(unused_explicit_netns_id(&BTreeSet::from([i32::MAX as u32]), i32::MAX as u32), Some(1));
        assert_eq!(unused_explicit_netns_id(&BTreeSet::new(), 0), None);
        assert_eq!(unused_explicit_netns_id(&BTreeSet::new(), u32::MAX), None);
    }
}
