//! Eventide: before a supported Fabric instance launches, put the in-game Eventide mod in its
//! mods folder and make sure Fabric API (which the mod needs) is installed. The first time an
//! instance launches it also gets a small performance pack from Modrinth.

use crate::event::InstancePayloadType;
use crate::event::emit::emit_instance;
use crate::state::instances::InstanceLaunchContext;
use crate::state::instances::commands::{
    InstanceInstallProjectRequest, get_installed_project_ids_for_instance,
    install_resolved_content_plan, resolve_install_plan,
};
use crate::state::{InstanceLink, ModLoader, State};
use crate::util::io;
use modrinth_content_management::{ContentType, ResolutionPreferences};
use std::path::Path;
use std::time::Duration;
use tracing::{info, warn};

const MOD_FILE_NAME: &str = "eventide.jar";
/// What the mod's jar was called before 3vhy Client became Eventide; old copies are cleared out.
const OLD_MOD_FILE_PREFIXES: &[&str] = &["eventide-client", "3vhy-client", "threevhy-client"];
const FABRIC_API: &str = "P7dR8mSH";
/// Sodium, Lithium, FerriteCore, Entity Culling, ImmediatelyFast
const PERFORMANCE_PACK: &[&str] =
    &["AANobbMI", "gvQqBUqZ", "uXXizFIs", "NNAgCjsB", "5ZwdcRci"];
const PERFORMANCE_PACK_MARKER: &str = ".eventide-performance-pack";
/// The same marker from before the rename, so instances that already have the pack aren't given it again.
const OLD_PERFORMANCE_PACK_MARKER: &str = ".3vhy-performance-pack";
const INSTALL_TIMEOUT: Duration = Duration::from_secs(90);

static MOD_1_21_11: &[u8] =
    include_bytes!(concat!(env!("OUT_DIR"), "/eventide-1.21.11.jar"));
static MOD_26_1: &[u8] =
    include_bytes!(concat!(env!("OUT_DIR"), "/eventide-26.1.jar"));
static MOD_26_2: &[u8] =
    include_bytes!(concat!(env!("OUT_DIR"), "/eventide-26.2.jar"));

/// The Eventide mod jar built for this Minecraft version, if Eventide supports it.
pub fn mod_jar_for(game_version: &str) -> Option<&'static [u8]> {
    let jar = match game_version {
        "1.21.11" => MOD_1_21_11,
        "26.1" | "26.1.1" | "26.1.2" => MOD_26_1,
        "26.2" => MOD_26_2,
        _ => return None,
    };
    (!jar.is_empty()).then_some(jar)
}

pub fn is_supported(loader: ModLoader, game_version: &str) -> bool {
    loader == ModLoader::Fabric && mod_jar_for(game_version).is_some()
}

/// Never fails the launch: anything that goes wrong is logged and the game starts without
/// Eventide's extras.
pub(crate) async fn prepare_launch(
    instance_id: &str,
    context: &InstanceLaunchContext,
    state: &State,
) {
    let content_set = &context.applied_content_set;
    if !is_supported(content_set.loader, &content_set.game_version) {
        return;
    }
    let Some(jar) = mod_jar_for(&content_set.game_version) else {
        return;
    };
    let instance_dir = state
        .directories
        .instances_dir()
        .join(&context.instance.path);
    let mods_dir = instance_dir.join("mods");

    let mut installed = installed_projects(instance_id, state).await;
    let mut changed = false;

    if !installed.iter().any(|id| id == FABRIC_API) {
        changed |= install(instance_id, FABRIC_API, state).await;
        installed = installed_projects(instance_id, state).await;
    }

    let gets_performance_pack =
        !matches!(context.link, InstanceLink::SharedInstance { .. })
            && !instance_dir.join(PERFORMANCE_PACK_MARKER).exists()
            && !instance_dir.join(OLD_PERFORMANCE_PACK_MARKER).exists();
    if gets_performance_pack {
        for project_id in PERFORMANCE_PACK {
            if !installed.iter().any(|id| id == project_id) {
                changed |= install(instance_id, project_id, state).await;
            }
        }
        if let Err(error) =
            io::write(instance_dir.join(PERFORMANCE_PACK_MARKER), b"").await
        {
            warn!("Eventide: couldn't record the performance pack: {error}");
        }
    }

    // Without Fabric API the mod would stop the game from starting, so leave it out
    let has_fabric_api = installed.iter().any(|id| id == FABRIC_API);
    match place_mod(&mods_dir, jar, has_fabric_api).await {
        Ok(placed) => changed |= placed,
        Err(error) => warn!("Eventide: couldn't add the Eventide mod: {error}"),
    }

    if changed
        && let Err(error) =
            emit_instance(instance_id, InstancePayloadType::Edited).await
    {
        warn!("Eventide: couldn't refresh the instance view: {error}");
    }
}

async fn installed_projects(instance_id: &str, state: &State) -> Vec<String> {
    get_installed_project_ids_for_instance(instance_id, None, state)
        .await
        .unwrap_or_else(|error| {
            warn!("Eventide: couldn't list installed content: {error}");
            Vec::new()
        })
}

/// Installs the newest version of a Modrinth project that fits the instance.
async fn install(instance_id: &str, project_id: &str, state: &State) -> bool {
    let work = async {
        let plan = resolve_install_plan(
            instance_id,
            InstanceInstallProjectRequest {
                project_id: project_id.to_string(),
                version_id: None,
                content_type: ContentType::Mod,
                selected: ResolutionPreferences::default(),
            },
            state,
        )
        .await?;
        install_resolved_content_plan(instance_id, &plan, state).await
    };
    match tokio::time::timeout(INSTALL_TIMEOUT, work).await {
        Ok(Ok(_)) => {
            info!("Eventide: installed {project_id} for {instance_id}");
            true
        }
        Ok(Err(error)) => {
            warn!("Eventide: couldn't install {project_id}: {error}");
            false
        }
        Err(_) => {
            warn!("Eventide: installing {project_id} took too long, skipping");
            false
        }
    }
}

/// Writes the bundled jar (when it changed) and clears out older copies. Returns whether
/// anything in the mods folder changed.
async fn place_mod(
    mods_dir: &Path,
    jar: &[u8],
    wanted: bool,
) -> crate::Result<bool> {
    io::create_dir_all(mods_dir).await?;
    let mut changed = false;
    let target = mods_dir.join(MOD_FILE_NAME);

    if let Ok(entries) = std::fs::read_dir(mods_dir) {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_lowercase();
            let is_ours = name == MOD_FILE_NAME
                || OLD_MOD_FILE_PREFIXES
                    .iter()
                    .any(|prefix| name.starts_with(prefix));
            if is_ours && entry.path() != target {
                io::remove_file(entry.path()).await?;
                changed = true;
            }
        }
    }

    if !wanted {
        if target.exists() {
            io::remove_file(&target).await?;
            changed = true;
        }
        return Ok(changed);
    }

    let up_to_date = tokio::fs::read(&target)
        .await
        .is_ok_and(|existing| existing == jar);
    if !up_to_date {
        io::write(&target, jar).await?;
        changed = true;
    }
    Ok(changed)
}
