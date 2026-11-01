#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build the standalone APK with SDK/NDK tools; no Gradle or Maven dependencies.

Requires JDK 17, Android platform 35, build-tools 35.0.0, NDK 27.2.12479018.
ANDROID_HOME selects the SDK. ANDROID_KEYSTORE may supply a persistent signing
key (alias androiddebugkey, standard development password android).
The default development key is generated once in build/android-signing/.
"""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
import wave

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "")))
OUT = ROOT / "build/android"
TOOLS = SDK / "build-tools/35.0.0"
PLATFORM = SDK / "platforms/android-35/android.jar"
NDK = SDK / "ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin"
# 32-bit ARM for the SM-T510 (32-bit userspace), 64-bit ARM for the Tab S6 Lite and newer tablets.
ABIS = [("armeabi-v7a", "armv7a-linux-androideabi28-clang"),
        ("arm64-v8a", "aarch64-linux-android28-clang")]

def run(*args):
    print("+", " ".join(map(str, args)), flush=True)
    subprocess.run(list(map(str, args)), cwd=ROOT, check=True)

def starter_samples(destination):
    destination.mkdir(parents=True,exist_ok=True)
    # The repository flute is an intentionally shortened source with its original
    # RIFF lengths. Write the available complete PCM frames with a correct header;
    # Android user imports remain strict about truncated files.
    for source,name in [("FLUTE/00_LDFlute_susvib_C4_v1_1.wav","Flute C4.wav"),
                        ("JUNGLE/00_JungleNight_C3.wav","Jungle C3.wav")]:
        with wave.open(str(ROOT/"assets/samples-cc0"/source),"rb") as original:
            frames=original.readframes(original.getnframes())
            width=original.getsampwidth()
            align=original.getnchannels()*width
            frames=frames[:len(frames)//align*align]
            # Give the bundled starters useful, consistent headroom. Imported
            # files keep their original level; this only prepares our two assets.
            values=[int.from_bytes(frames[i:i+width],"little",signed=True) for i in range(0,len(frames),width)]
            peak=max((abs(v) for v in values),default=0)
            if peak:
                gain=.75*((1<<(width*8-1))-1)/peak
                frames=b"".join(round(v*gain).to_bytes(width,"little",signed=True) for v in values)
            with wave.open(str(destination/name),"wb") as output:
                output.setparams(original.getparams())
                output.writeframes(frames[:len(frames)//align*align])

def PALEO_SOURCES():
    """The engine, the conductor, the bridge, and the Faust instruments PALEO uses (not the hall or echo)."""
    gen=sorted(p for p in (ROOT/"paleo/faust/gen").glob("*.cpp") if p.name[0] not in "he")
    gen+=[ROOT/"paleo/faust/gen/horn.cpp",ROOT/"paleo/faust/gen/harmonium.cpp"]
    return ["paleo/engine/paleo.cpp","paleo/engine/conductor.cpp","android/native/paleo_bridge.cpp"]+[str(p.relative_to(ROOT)) for p in gen]

INTERFACE_FONTS=["IBMPlexMono-Regular.ttf","IBMPlexMono-Medium.ttf","Michroma-Regular.ttf","Caveat[wght].ttf","IMFeENrm28P.ttf"]

def main():
    if not PLATFORM.is_file() or not all((NDK/compiler).is_file() for _,compiler in ABIS):
        raise SystemExit("Install the documented Android SDK/NDK packages and set ANDROID_HOME.")
    if OUT.exists(): shutil.rmtree(OUT)
    for name in [f"lib/{abi}" for abi,_ in ABIS]+["classes", "dex", "assets/licenses"]:
        (OUT/name).mkdir(parents=True, exist_ok=True)
    run("python3", "tools/build.py", "--gen-only")
    for abi,compiler in ABIS:
        library=OUT/"lib"/abi/"libpaleotone.so"
        flags=["-std=gnu11", "-O2", "-ffp-contract=off", "-fwrapv", "-fPIC", "-fvisibility=hidden",
            "-ffunction-sections", "-fdata-sections",
            "-Wno-incompatible-pointer-types-discards-qualifiers", "-Wno-unused-function",
            "-Ibuild/gen", "-Ifirmware/src"]
        # The display copy of the firmware: every symbol local but its entry points,
        # so it shares nothing with the engine's copy.
        display=OUT/"lib"/abi/"display.o"
        run(NDK/compiler, *flags, "-c", "android/native/display.c", "-o", display)
        run(NDK/"llvm-objcopy", "--wildcard", "--keep-global-symbol=pt_display_*", display)
        objects=[display]
        for source in ["android/native/engine.c", "android/native/android_audio.c"]:
            objects.append(OUT/"lib"/abi/(Path(source).stem+".o"))
            run(NDK/compiler, *flags, "-c", source, "-o", objects[-1])
        # PALEO and the live Scape conductor (C++17), with the approved Faust instruments.
        cxx=[NDK/(compiler+"++"), "-std=c++17", "-O2", "-fPIC", "-fvisibility=hidden", "-w",
            "-ffunction-sections", "-fdata-sections", "-Ipaleo"]
        if abi=="armeabi-v7a":cxx+=["-mfpu=neon", "-mfloat-abi=softfp"]
        for source in PALEO_SOURCES():
            objects.append(OUT/"lib"/abi/("paleo-"+Path(source).stem+".o"))
            run(*cxx, "-c", source, "-o", objects[-1])
        run(NDK/(compiler+"++"), "-shared", "-static-libstdc++", "-Wl,--gc-sections", "-Wl,-z,max-page-size=16384",
            *objects, "-laaudio", "-llog", "-landroid", "-lm", "-o", library)
        for o in objects:o.unlink()
        run(NDK/"llvm-strip", "--strip-unneeded", library)
    java = sorted((ROOT/"android/java").rglob("*.java"))
    run("javac", "-encoding", "UTF-8", "--release", "8", "-classpath", PLATFORM,
        "-d", OUT/"classes", *java)
    run(TOOLS/"d8", "--min-api", "28", "--lib", PLATFORM, "--output", OUT/"dex",
        *sorted((OUT/"classes").rglob("*.class")))
    shutil.copy(ROOT/"LICENSE", OUT/"assets/licenses/GPL-3.0.txt")
    for file in (ROOT/"LICENSES").iterdir():
        if file.is_file():shutil.copy(file,OUT/"assets/licenses"/file.name)
    shutil.copy(ROOT/"LICENSING.md",OUT/"assets/licenses/LICENSING.md")
    shutil.copy(ROOT/"assets/samples-cc0/ATTRIBUTION.txt",OUT/"assets/licenses/SAMPLES.txt")
    starter_samples(OUT/"assets/starter")
    # PALEO's creature library (FLAC, read straight from the APK) and its credits.
    shutil.copytree(ROOT/"paleo/creatures/lib",OUT/"assets/creatures")
    shutil.copy(ROOT/"paleo/creatures/CREDITS.md",OUT/"assets/licenses/CREATURES.md")
    # The round-three interface fonts (SIL OFL 1.1; licences are in assets/licenses).
    (OUT/"assets/fonts").mkdir(parents=True,exist_ok=True)
    for name in INTERFACE_FONTS:shutil.copy(ROOT/"assets/fonts"/name,OUT/"assets/fonts"/name)
    (OUT/"assets/BUILD.txt").write_text(
        "Paleotone Android 0.4.0-alpha\nGPL-3.0-only\n"+
        subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT,text=True).strip()+
        "\nBase: 0390a03f732c5eeec9f62cb2b139df8738396766\n"+
        "Local modifications are included in the corresponding source archive.\n")
    run(TOOLS/"aapt2", "compile", "--dir", ROOT/"android/res", "-o", OUT/"resources.zip")
    run(TOOLS/"aapt2", "link", "-o", OUT/"unsigned.apk", "-I", PLATFORM,
        "--manifest", ROOT/"android/AndroidManifest.xml", "-A", OUT/"assets", OUT/"resources.zip")
    with zipfile.ZipFile(OUT/"unsigned.apk","a",zipfile.ZIP_DEFLATED) as archive:
        archive.write(OUT/"dex/classes.dex","classes.dex")
        for abi,_ in ABIS:
            archive.write(OUT/"lib"/abi/"libpaleotone.so",f"lib/{abi}/libpaleotone.so")
    run(TOOLS/"zipalign", "-f", "-P", "16", "4", OUT/"unsigned.apk", OUT/"aligned.apk")
    key=Path(os.environ.get("ANDROID_KEYSTORE",str(ROOT/"build/android-signing/debug.keystore")))
    if not key.exists():
        key.parent.mkdir(parents=True,exist_ok=True)
        run("keytool","-genkeypair","-keystore",key,"-storepass","android","-keypass","android",
            "-alias","androiddebugkey","-keyalg","RSA","-keysize","2048","-validity","10000",
            "-dname","CN=Paleotone Development,O=Paleotone,C=US")
    apk=OUT/"Paleotone-Android-0.4.0-alpha.apk"
    run(TOOLS/"apksigner","sign","--ks",key,"--ks-pass","pass:android","--key-pass","pass:android",
        "--out",apk,OUT/"aligned.apk")
    run(TOOLS/"apksigner","verify","--verbose",apk)
    with zipfile.ZipFile(apk) as archive:
        missing=[abi for abi,_ in ABIS if f"lib/{abi}/libpaleotone.so" not in archive.namelist()]
    if missing:raise SystemExit("APK is missing native libraries for: "+", ".join(missing))
    (OUT/"SHA256SUMS").write_text(hashlib.sha256(apk.read_bytes()).hexdigest()+"  "+apk.name+"\n")
    print(apk)

if __name__=="__main__":main()
