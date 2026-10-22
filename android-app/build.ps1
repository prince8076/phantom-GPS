$ErrorActionPreference = "Stop"

$BUILD_TOOLS = "C:\Users\rahul\AppData\Local\Android\Sdk\build-tools\34.0.0"
$ANDROID_JAR = "C:\Users\rahul\AppData\Local\Android\Sdk\platforms\android-34\android.jar"
$JAVAC = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\javac.exe"
$KEYTOOL = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\keytool.exe"

$ROOT = "c:\Users\rahul\omnirush.ai\android-app"
$BUILD_DIR = "$ROOT\build"
$COMPILED_RES = "$BUILD_DIR\compiled_res"
$GEN_DIR = "$BUILD_DIR\gen"
$CLASSES_DIR = "$BUILD_DIR\classes"
$OUTPUT_APK = "$BUILD_DIR\OmniRush-GPS-unaligned.apk"
$ALIGNED_APK = "$BUILD_DIR\OmniRush-GPS.apk"
$PUBLIC_DIR = "c:\Users\rahul\omnirush.ai\desktop-app\public"

# WireGuard tunnel library (GPL-2.0, see VPN_CORE_LICENSE_NOTE.md).
# Download tunnel-1.0.20230706.aar from Maven Central:
#   https://repo1.maven.org/maven2/com/wireguard/android/tunnel/1.0.20230706/tunnel-1.0.20230706.aar
# and place it at android-app\libs\tunnel.aar (exact filename).
$TUNNEL_AAR = "$ROOT\libs\tunnel.aar"
$TUNNEL_DIR = "$BUILD_DIR\tunnel"
$TUNNEL_JAR = "$TUNNEL_DIR\classes.jar"

# Fail fast on native tool errors (PowerShell does not stop on native
# non-zero exits by itself, which previously produced broken APKs silently).
function Assert-Ok([string]$step) {
    if ($LASTEXITCODE -ne 0) { throw "$step failed with exit code $LASTEXITCODE — see the error output above." }
}

Write-Host "Cleaning and creating build directories..."
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $CLASSES_DIR, $GEN_DIR, $COMPILED_RES, $TUNNEL_DIR
New-Item -ItemType Directory -Force -Path $BUILD_DIR | Out-Null
New-Item -ItemType Directory -Force -Path $COMPILED_RES | Out-Null
New-Item -ItemType Directory -Force -Path $GEN_DIR | Out-Null
New-Item -ItemType Directory -Force -Path $CLASSES_DIR | Out-Null

Write-Host "Step 0: WireGuard tunnel library..."
if (-not (Test-Path $TUNNEL_AAR)) {
    throw "Missing $TUNNEL_AAR — the VPN core (WireGuardGoBackend) needs com.wireguard.android:tunnel. Download tunnel-1.0.20230706.aar from Maven Central and save it as android-app\libs\tunnel.aar"
}
New-Item -ItemType Directory -Force -Path $TUNNEL_DIR | Out-Null
[Reflection.Assembly]::LoadWithPartialName('System.IO.Compression.FileSystem') | Out-Null
[System.IO.Compression.ZipFile]::ExtractToDirectory($TUNNEL_AAR, $TUNNEL_DIR)
if (-not (Test-Path $TUNNEL_JAR)) { throw "tunnel.aar did not contain classes.jar" }
Write-Host "Tunnel AAR extracted."

Write-Host "Step 1: Compiling resources with aapt2..."
& "$BUILD_TOOLS\aapt2.exe" compile --dir "$ROOT\src\main\res" -o "$COMPILED_RES\res.zip"
Assert-Ok "aapt2 compile"

Write-Host "Step 2: Linking APK with aapt2..."
Remove-Item -Force -ErrorAction SilentlyContinue $OUTPUT_APK
& "$BUILD_TOOLS\aapt2.exe" link -I $ANDROID_JAR --manifest "$ROOT\src\main\AndroidManifest.xml" `
    --java $GEN_DIR -o "$OUTPUT_APK" "$COMPILED_RES\res.zip" --auto-add-overlay
Assert-Ok "aapt2 link"

Write-Host "Step 3: Compiling Java sources with javac..."
$javaFiles = Get-ChildItem -Recurse -Filter "*.java" "$ROOT\src\main\java", $GEN_DIR | Select-Object -ExpandProperty FullName
& $JAVAC -source 8 -target 8 -bootclasspath $ANDROID_JAR -classpath $TUNNEL_JAR -d $CLASSES_DIR $javaFiles
Assert-Ok "javac"
# Guard: the launcher activity must have compiled, otherwise the APK will
# crash on launch with ClassNotFoundException.
$launcherClass = "$CLASSES_DIR\ai\omnirush\chameleon\MainActivity.class"
if (-not (Test-Path $launcherClass)) { throw "javac did not produce MainActivity.class — compilation failed silently. See javac errors above." }

Write-Host "Step 4: Converting classes to DEX with d8..."
$classFiles = Get-ChildItem -Recurse -Filter "*.class" $CLASSES_DIR | Select-Object -ExpandProperty FullName
& "$BUILD_TOOLS\d8.bat" --lib $ANDROID_JAR --output $BUILD_DIR $classFiles $TUNNEL_JAR
Assert-Ok "d8"
if (-not (Test-Path "$BUILD_DIR\classes.dex")) { throw "d8 did not produce classes.dex." }

Write-Host "Step 5: Packaging classes.dex and native libs into APK..."
[Reflection.Assembly]::LoadWithPartialName('System.IO.Compression') | Out-Null
[Reflection.Assembly]::LoadWithPartialName('System.IO.Compression.FileSystem') | Out-Null
$mode = [System.IO.Compression.ZipArchiveMode]::Update
$zipArchive = [System.IO.Compression.ZipFile]::Open($OUTPUT_APK, $mode)
$existing = $zipArchive.GetEntry("classes.dex")
if ($existing) { $existing.Delete() }
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zipArchive, "$BUILD_DIR\classes.dex", "classes.dex")
Write-Host "classes.dex injected successfully into APK."
$jniDir = "$TUNNEL_DIR\jni"
if (Test-Path $jniDir) {
    Get-ChildItem -Directory $jniDir | ForEach-Object {
        $abi = $_.Name
        Get-ChildItem -Filter "*.so" $_.FullName | ForEach-Object {
            $entryName = "lib/$abi/$($_.Name)"
            $old = $zipArchive.GetEntry($entryName)
            if ($old) { $old.Delete() }
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zipArchive, $_.FullName, $entryName)
            Write-Host "  + $entryName"
        }
    }
    Write-Host "WireGuard native libraries injected."
}
$zipArchive.Dispose()

Write-Host "Step 6: Zipalign APK..."
Remove-Item -Force -ErrorAction SilentlyContinue $ALIGNED_APK
& "$BUILD_TOOLS\zipalign.exe" -v -p 4 "$OUTPUT_APK" "$ALIGNED_APK"
Assert-Ok "zipalign"

Write-Host "Step 7: Checking debug keystore..."
$KEYSTORE = "$BUILD_DIR\debug.keystore"
if (-not (Test-Path $KEYSTORE)) {
    & $KEYTOOL -genkeypair -v -keystore $KEYSTORE -alias androiddebugkey -keypass android -storepass android -dname "CN=OmniRush, OU=Security, O=OmniRush, C=US" -validity 10000 -keyalg RSA -keysize 2048
}

Write-Host "Step 8: Signing APK with apksigner..."
& "$BUILD_TOOLS\apksigner.bat" sign --ks $KEYSTORE --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey "$ALIGNED_APK"
Assert-Ok "apksigner"

Write-Host "Step 9: Publishing to desktop-app/public..."
Copy-Item -Force $ALIGNED_APK "$PUBLIC_DIR\OmniRush-GPS.apk"
Write-Host "=== SUCCESS: OmniRush-GPS.apk is ready at $PUBLIC_DIR\OmniRush-GPS.apk ==="

