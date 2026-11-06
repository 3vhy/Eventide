use std::ffi::OsString;
use std::path::PathBuf;
use std::process::{Command, exit};
use std::{env, fs};

fn main() {
    println!("cargo::rerun-if-changed=.env");
    println!("cargo::rerun-if-changed=java/gradle");
    println!("cargo::rerun-if-changed=java/src");
    println!("cargo::rerun-if-changed=java/build.gradle.kts");
    println!("cargo::rerun-if-changed=java/settings.gradle.kts");
    println!("cargo::rerun-if-changed=java/gradle.properties");

    set_env();
    build_java_jars();
    embed_eventide_mods();
}

/// Eventide: the in-game mod jars (built by the Eventide mod workflow and copied into
/// eventide-mod/) are embedded in the launcher. Missing jars embed as empty files, which
/// just means Eventide's in-game features are left out of that build.
fn embed_eventide_mods() {
    println!("cargo::rerun-if-changed=eventide-mod");
    let out_dir = PathBuf::from(env::var_os("OUT_DIR").unwrap());
    for version in ["1.21.11", "26.1", "26.2"] {
        let name = format!("eventide-{version}.jar");
        let source = PathBuf::from("eventide-mod").join(&name);
        let target = out_dir.join(&name);
        if source.is_file() {
            fs::copy(&source, &target)
                .expect("failed to copy the Eventide mod jar");
        } else {
            println!(
                "cargo::warning=Eventide mod for Minecraft {version} not found at eventide-mod/{name}; building without it"
            );
            fs::write(&target, b"").unwrap();
        }
    }
}

fn set_env() {
    for (var_name, var_value) in
        dotenvy::dotenv_iter().into_iter().flatten().flatten()
    {
        if var_name == "DATABASE_URL" {
            // The sqlx database URL is a build-time detail that should not be exposed to the crate
            continue;
        }

        println!("cargo::rustc-env={var_name}={var_value}");
    }
}

fn build_java_jars() {
    let out_dir =
        dunce::canonicalize(PathBuf::from(env::var_os("OUT_DIR").unwrap()))
            .unwrap();

    println!(
        "cargo::rustc-env=JAVA_JARS_DIR={}",
        out_dir.join("java/libs").display()
    );

    let gradle_path = fs::canonicalize(
        #[cfg(target_os = "windows")]
        "java\\gradlew.bat",
        #[cfg(not(target_os = "windows"))]
        "java/gradlew",
    )
    .unwrap();

    let mut build_dir_str = OsString::from("-Dorg.gradle.project.buildDir=");
    build_dir_str.push(out_dir.join("java"));
    let exit_status = Command::new(gradle_path)
        .arg(build_dir_str)
        .arg("build")
        .arg("--no-daemon")
        .arg("--console=rich")
        .current_dir(dunce::canonicalize("java").unwrap())
        .status()
        .expect("Failed to wait on Gradle build");

    if !exit_status.success() {
        println!("cargo::error=Gradle build failed with {exit_status}");
        exit(exit_status.code().unwrap_or(1));
    }
}
