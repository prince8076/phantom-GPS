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

Write-Host "Cleaning and creating build directories..."
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $CLASSES_DIR, $GEN_DIR, $COMPILED_RES
New-Item -ItemType Directory -Force -Path $BUILD_DIR | Out-Null
New-Item -ItemType Directory -Force -Path $COMPILED_RES | Out-Null
New-Item -ItemType Directory -Force -Path $GEN_DIR | Out-Null
New-Item -ItemType Directory -Force -Path $CLASSES_DIR | Out-Null

Write-Host "Step 1: Compiling resources with aapt2..."
& "$BUILD_TOOLS\aapt2.exe" compile --dir "$ROOT\src\main\res" -o "$COMPILED_RES\res.zip"

Write-Host "Step 2: Linking APK with aapt2..."
Remove-Item -Force -ErrorAction SilentlyContinue $OUTPUT_APK
& "$BUILD_TOOLS\aapt2.exe" link -I $ANDROID_JAR --manifest "$ROOT\src\main\AndroidManifest.xml" `
    --java $GEN_DIR -o "$OUTPUT_APK" "$COMPILED_RES\res.zip" --auto-add-overlay

Write-Host "Step 3: Compiling Java sources with javac..."
$javaFiles = Get-ChildItem -Recurse -Filter "*.java" "$ROOT\src\main\java", $GEN_DIR | Select-Object -ExpandProperty FullName
& $JAVAC -source 8 -target 8 -bootclasspath $ANDROID_JAR -d $CLASSES_DIR $javaFiles

Write-Host "Step 4: Converting classes to DEX with d8..."
$classFiles = Get-ChildItem -Recurse -Filter "*.class" $CLASSES_DIR | Select-Object -ExpandProperty FullName
& "$BUILD_TOOLS\d8.bat" --lib $ANDROID_JAR --output $BUILD_DIR $classFiles

Write-Host "Step 5: Packaging classes.dex into APK..."
[Reflection.Assembly]::LoadWithPartialName('System.IO.Compression') | Out-Null
[Reflection.Assembly]::LoadWithPartialName('System.IO.Compression.FileSystem') | Out-Null
$mode = [System.IO.Compression.ZipArchiveMode]::Update
$zipArchive = [System.IO.Compression.ZipFile]::Open($OUTPUT_APK, $mode)
$existing = $zipArchive.GetEntry("classes.dex")
if ($existing) { $existing.Delete() }
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zipArchive, "$BUILD_DIR\classes.dex", "classes.dex")
$zipArchive.Dispose()
Write-Host "classes.dex injected successfully into APK."

Write-Host "Step 6: Zipalign APK..."
Remove-Item -Force -ErrorAction SilentlyContinue $ALIGNED_APK
& "$BUILD_TOOLS\zipalign.exe" -v -p 4 "$OUTPUT_APK" "$ALIGNED_APK"

Write-Host "Step 7: Checking debug keystore..."
$KEYSTORE = "$BUILD_DIR\debug.keystore"
if (-not (Test-Path $KEYSTORE)) {
    & $KEYTOOL -genkeypair -v -keystore $KEYSTORE -alias androiddebugkey -keypass android -storepass android -dname "CN=OmniRush, OU=Security, O=OmniRush, C=US" -validity 10000 -keyalg RSA -keysize 2048
}

Write-Host "Step 8: Signing APK with apksigner..."
& "$BUILD_TOOLS\apksigner.bat" sign --ks $KEYSTORE --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey "$ALIGNED_APK"

Write-Host "Step 9: Publishing to desktop-app/public..."
Copy-Item -Force $ALIGNED_APK "$PUBLIC_DIR\OmniRush-GPS.apk"
Write-Host "=== SUCCESS: OmniRush-GPS.apk is ready at $PUBLIC_DIR\OmniRush-GPS.apk ==="

