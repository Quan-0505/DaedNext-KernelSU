use super::*;
use std::sync::Weak;
use std::sync::atomic::{AtomicBool, Ordering as AtomicOrdering};

#[derive(Debug)]
pub(super) struct ProductRuntimeStartupRecoverySupervisor {
    stop: Arc<AtomicBool>,
    handle: Option<thread::JoinHandle<()>>,
}

impl Default for ProductRuntimeStartupRecoverySupervisor {
    fn default() -> Self {
        Self {
            stop: Arc::new(AtomicBool::new(false)),
            handle: None,
        }
    }
}

impl ProductRuntimeStartupRecoverySupervisor {
    pub(super) fn start(
        runtime: Weak<ProductRuntimeManager>,
        state: PathBuf,
        config_dir: PathBuf,
    ) -> io::Result<Self> {
        let stop = Arc::new(AtomicBool::new(false));
        let thread_stop = Arc::clone(&stop);
        let handle = thread::Builder::new()
            .name("product-runtime-startup-recovery".to_owned())
            .spawn(move || {
                let mut last_attempt = None::<Instant>;
                while !thread_stop.load(AtomicOrdering::Relaxed) {
                    let Some(runtime) = runtime.upgrade() else {
                        return;
                    };
                    let retry_ready = last_attempt
                        .map(|attempt| {
                            attempt.elapsed() >= PRODUCT_RUNTIME_INTERFACE_RECOVERY_RETRY
                        })
                        .unwrap_or(true);
                    if retry_ready
                        && runtime.startup_restore_needed()
                        && should_restore_runtime_on_start(&state).unwrap_or(false)
                    {
                        let result = restore_runtime_from_state(
                            &runtime,
                            &state,
                            Some(&config_dir),
                            ProductRuntimeLifecycleLogMode::StartupRestore,
                        );
                        if let Err(err) = result {
                            record_startup_runtime_restore_failure(&config_dir, &state, &err);
                            last_attempt = Some(Instant::now());
                        } else {
                            last_attempt = None;
                        }
                    }
                    drop(runtime);
                    sleep_startup_recovery_poll(&thread_stop);
                }
            })?;
        Ok(Self {
            stop,
            handle: Some(handle),
        })
    }

    pub(super) fn shutdown(&mut self) {
        self.stop.store(true, AtomicOrdering::Relaxed);
        if let Some(handle) = self.handle.take() {
            handle.thread().unpark();
            let _ = handle.join();
        }
    }
}

fn sleep_startup_recovery_poll(stop: &AtomicBool) {
    let deadline = Instant::now() + PRODUCT_RUNTIME_STARTUP_RECOVERY_POLL;
    while !stop.load(AtomicOrdering::Relaxed) {
        let now = Instant::now();
        if now >= deadline {
            return;
        }
        thread::park_timeout(deadline - now);
    }
}

impl ProductRuntimeManager {
    fn startup_restore_needed(&self) -> bool {
        // A physical replacement temporarily leaves the runtime slot empty,
        // including after cleanup has finished but before the new bind starts.
        let apply = self.domain.reconciler().summary();
        if apply["state"] != "idle" || apply["preparingCount"] != 0 {
            return false;
        }
        self.inner()
            .lock()
            .map(|state| {
                state.runtime.is_none() && !state.cleanup.running && !state.network_policy_paused
            })
            .unwrap_or(false)
    }

    pub(in crate::daed_product) fn start_startup_recovery(
        self: &Arc<Self>,
        state: PathBuf,
        config_dir: PathBuf,
    ) -> io::Result<()> {
        let supervisor = ProductRuntimeStartupRecoverySupervisor::start(
            Arc::downgrade(self),
            state,
            config_dir,
        )?;
        let mut current = self
            .startup_recovery
            .lock()
            .map_err(|_| io::Error::other("startup recovery supervisor lock poisoned"))?;
        current.shutdown();
        *current = supervisor;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn startup_recovery_does_not_queue_behind_an_active_interface_rebind() {
        let runtime = ProductRuntimeManager::new();
        let permit = runtime
            .domain
            .reconciler()
            .begin_exclusive(RuntimeApplyIntent::InterfaceRecovery)
            .unwrap();
        assert!(!runtime.startup_restore_needed());
        permit.finish("succeeded");
        assert!(runtime.startup_restore_needed());
    }

    #[test]
    fn startup_recovery_waits_for_interface_replacement_cleanup() {
        let runtime = ProductRuntimeManager::new();
        assert!(runtime.startup_restore_needed());
        runtime
            .inner()
            .lock()
            .unwrap()
            .cleanup
            .begin(1, "reload-replace");
        assert!(!runtime.startup_restore_needed());
    }
}
