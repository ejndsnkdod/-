# Album Cleaner Build Script
$SDK = "C:\Users\WERDF\AppData\Local\Android\Sdk"
$BUILD_TOOLS = "$SDK\build-tools\37.0.0"
$PLATFORM = "$SDK\platforms\android-36.1"
$PROJECT_DIR = $PSScriptRoot
$APP_DIR = "$PROJECT_DIR\app"
$JAVA_BIN = "E:\android\jbr\bin"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Album Cleaner APK Builder" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# Create output directories
$dirs = @("$APP_DIR\build", "$APP_DIR\build\intermediates", "$APP_DIR\build\outputs", "$APP_DIR\build\classes")
foreach ($dir in $dirs) {
    if (!(Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
}

# Step 1: Compile resources
Write-Host "`n[1/6] Compiling resources..." -ForegroundColor Yellow
& "$BUILD_TOOLS\aapt2.exe" compile -o "$APP_DIR\build\intermediates\res.zip" --dir "$APP_DIR\src\main\res"
if ($LASTEXITCODE -ne 0) { Write-Error "Resource compilation failed"; exit 1 }

# Step 2: Link resources
Write-Host "[2/6] Linking resources..." -ForegroundColor Yellow
& "$BUILD_TOOLS\aapt2.exe" link -o "$APP_DIR\build\intermediates\resources.ap_" -I "$PLATFORM\android.jar" --manifest "$APP_DIR\src\main\AndroidManifest.xml" --java "$APP_DIR\build\intermediates" "$APP_DIR\build\intermediates\res.zip"
if ($LASTEXITCODE -ne 0) { Write-Error "Resource linking failed"; exit 1 }

# Step 3: Compile Java
Write-Host "[3/6] Compiling Java sources..." -ForegroundColor Yellow
$javaFiles = Get-ChildItem -Path "$APP_DIR\src\main\java" -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
$javaFileList = $javaFiles -join " "
& "$JAVA_BIN\javac.exe" -encoding UTF-8 -cp "$PLATFORM\android.jar;$APP_DIR\build\intermediates" -d "$APP_DIR\build\classes" $javaFiles
if ($LASTEXITCODE -ne 0) { Write-Error "Java compilation failed"; exit 1 }

# Step 4: Convert to DEX
Write-Host "[4/6] Converting to DEX..." -ForegroundColor Yellow
$classFiles = Get-ChildItem -Path "$APP_DIR\build\classes\com\example\albumcleaner" -Filter "*.class" | Select-Object -ExpandProperty FullName
& "$BUILD_TOOLS\d8.bat" --release --output "$APP_DIR\build\intermediates" $classFiles
if ($LASTEXITCODE -ne 0) { Write-Error "DEX conversion failed"; exit 1 }

# Step 5: Package APK
Write-Host "[5/6] Packaging APK..." -ForegroundColor Yellow
Copy-Item "$APP_DIR\build\intermediates\resources.ap_" "$APP_DIR\build\outputs\app-unsigned.apk" -Force
$originalDir = Get-Location
Set-Location "$APP_DIR\build\intermediates"
& "$BUILD_TOOLS\aapt.exe" add "$APP_DIR\build\outputs\app-unsigned.apk" classes.dex
Set-Location $originalDir
if ($LASTEXITCODE -ne 0) { Write-Error "APK packaging failed"; exit 1 }

# Step 6: Sign APK
Write-Host "[6/6] Signing APK..." -ForegroundColor Yellow
$keystore = "$APP_DIR\build\keystore.jks"
if (!(Test-Path $keystore)) {
    Write-Host "Generating keystore..." -ForegroundColor Gray
    & "$JAVA_BIN\keytool.exe" -genkey -v -keystore $keystore -alias albumcleaner -keyalg RSA -keysize 2048 -validity 10000 -storepass "123456" -keypass "123456" -dname "CN=AlbumCleaner"
}
& "$BUILD_TOOLS\apksigner.bat" sign --ks $keystore --ks-pass pass:123456 --key-pass pass:123456 --out "$APP_DIR\build\outputs\app-release.apk" "$APP_DIR\build\outputs\app-unsigned.apk"
if ($LASTEXITCODE -ne 0) { Write-Error "APK signing failed"; exit 1 }

Write-Host "`n=========================================" -ForegroundColor Green
Write-Host "Build Successful!" -ForegroundColor Green
Write-Host "APK: $APP_DIR\build\outputs\app-release.apk" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
