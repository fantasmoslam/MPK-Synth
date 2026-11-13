#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build the MPK Synth APK with SDK/NDK tools; no Gradle or Maven dependencies.

Requires JDK 17, Android platform 35, build-tools 35.0.0, NDK 27.2.12479018.
ANDROID_HOME selects the SDK. ANDROID_KEYSTORE may supply a persistent signing
key (alias androiddebugkey, standard development password android).
The default development key is generated once in build/android-signing/.
"""
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
APP_NAME = "MPK Synth"
SDK = Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "")))
OUT = ROOT / "build/android"
TOOLS = SDK / "build-tools/35.0.0"
PLATFORM = SDK / "platforms/android-35/android.jar"
NDK = SDK / "ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin"
# 32-bit ARM for older tablets (32-bit userspace), 64-bit ARM for newer ones.
ABIS = [("armeabi-v7a", "armv7a-linux-androideabi28-clang"),
        ("arm64-v8a", "aarch64-linux-android28-clang")]
LIBRARY = "libmpksynth.so"
NATIVE = ["android/native/synth.c"] + sorted(str(p.relative_to(ROOT)) for p in (ROOT/"android/native/mpk").glob("*.c"))
FONTS = ["IBMPlexMono-Regular.ttf", "IBMPlexMono-Medium.ttf", "Michroma-Regular.ttf"]

def run(*args):
    print("+", " ".join(map(str, args)), flush=True)
    subprocess.run(list(map(str, args)), cwd=ROOT, check=True)

def version():
    manifest=(ROOT/"android/AndroidManifest.xml").read_text()
    return re.search(r'android:versionName="([^"]+)"',manifest).group(1)

def main():
    if not PLATFORM.is_file() or not all((NDK/compiler).is_file() for _,compiler in ABIS):
        raise SystemExit("Install the documented Android SDK/NDK packages and set ANDROID_HOME.")
    if OUT.exists(): shutil.rmtree(OUT)
    for name in [f"lib/{abi}" for abi,_ in ABIS]+["classes", "dex", "assets/licenses", "assets/fonts"]:
        (OUT/name).mkdir(parents=True, exist_ok=True)
    for abi,compiler in ABIS:
        flags=["-std=gnu11", "-O2", "-fPIC", "-fvisibility=hidden", "-Wall", "-Wno-implicit-fallthrough",
            "-ffunction-sections", "-fdata-sections"]
        if abi=="armeabi-v7a":flags+=["-mfpu=neon", "-mfloat-abi=softfp"]
        run(NDK/compiler, *flags, "-shared", "-Wl,--gc-sections", "-Wl,-z,max-page-size=16384",
            *NATIVE, "-laaudio", "-llog", "-lm", "-o", OUT/"lib"/abi/LIBRARY)
        run(NDK/"llvm-strip", "--strip-unneeded", OUT/"lib"/abi/LIBRARY)
    java = sorted((ROOT/"android/java").rglob("*.java"))
    run("javac", "-encoding", "UTF-8", "--release", "8", "-classpath", PLATFORM,
        "-d", OUT/"classes", *java)
    run(TOOLS/"d8", "--min-api", "28", "--lib", PLATFORM, "--output", OUT/"dex",
        *sorted((OUT/"classes").rglob("*.class")))
    shutil.copy(ROOT/"LICENSE", OUT/"assets/licenses/GPL-3.0.txt")
    for file in (ROOT/"LICENSES").iterdir():
        if file.is_file():shutil.copy(file,OUT/"assets/licenses"/file.name)
    shutil.copy(ROOT/"LICENSING.md",OUT/"assets/licenses/LICENSING.md")
    for name in FONTS:shutil.copy(ROOT/"assets/fonts"/name,OUT/"assets/fonts"/name)
    (OUT/"assets/BUILD.txt").write_text(
        APP_NAME+" "+version()+"\nGPL-3.0-only\n"+
        subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT,text=True).strip()+"\n")
    run(TOOLS/"aapt2", "link", "-o", OUT/"unsigned.apk", "-I", PLATFORM,
        "--manifest", ROOT/"android/AndroidManifest.xml", "-A", OUT/"assets")
    with zipfile.ZipFile(OUT/"unsigned.apk","a",zipfile.ZIP_DEFLATED) as archive:
        archive.write(OUT/"dex/classes.dex","classes.dex")
        for abi,_ in ABIS:
            archive.write(OUT/"lib"/abi/LIBRARY,f"lib/{abi}/{LIBRARY}")
    run(TOOLS/"zipalign", "-f", "-P", "16", "4", OUT/"unsigned.apk", OUT/"aligned.apk")
    key=Path(os.environ.get("ANDROID_KEYSTORE",str(ROOT/"build/android-signing/debug.keystore")))
    if not key.exists():
        key.parent.mkdir(parents=True,exist_ok=True)
        run("keytool","-genkeypair","-keystore",key,"-storepass","android","-keypass","android",
            "-alias","androiddebugkey","-keyalg","RSA","-keysize","2048","-validity","10000",
            "-dname","CN=MPK Synth Development,O=MPK Synth,C=US")
    apk=OUT/(APP_NAME.replace(" ","-")+"-"+version()+".apk")
    run(TOOLS/"apksigner","sign","--ks",key,"--ks-pass","pass:android","--key-pass","pass:android",
        "--out",apk,OUT/"aligned.apk")
    run(TOOLS/"apksigner","verify","--verbose",apk)
    with zipfile.ZipFile(apk) as archive:
        missing=[abi for abi,_ in ABIS if f"lib/{abi}/{LIBRARY}" not in archive.namelist()]
    if missing:raise SystemExit("APK is missing native libraries for: "+", ".join(missing))
    (OUT/"SHA256SUMS").write_text(hashlib.sha256(apk.read_bytes()).hexdigest()+"  "+apk.name+"\n")
    print(apk)

if __name__=="__main__":main()
