@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

:: 设置路径
set "SDK=C:\Users\WERDF\AppData\Local\Android\Sdk"
set "BUILD_TOOLS=%SDK%\build-tools\37.0.0"
set "PLATFORM=%SDK%\platforms\android-36.1"
set "PROJECT_DIR=%CD%"
set "APP_DIR=%PROJECT_DIR%\app"

:: 创建输出目录
if not exist "%APP_DIR%\build" mkdir "%APP_DIR%\build"
if not exist "%APP_DIR%\build\intermediates" mkdir "%APP_DIR%\build\intermediates"
if not exist "%APP_DIR%\build\outputs" mkdir "%APP_DIR%\build\outputs"

echo [1/6] 正在编译资源...
"%BUILD_TOOLS%\aapt2.exe" compile -o "%APP_DIR%\build\intermediates\res.zip" --dir "%APP_DIR%\src\main\res"
if errorlevel 1 (
    echo 资源编译失败
    exit /b 1
)

echo [2/6] 正在链接资源...
"%BUILD_TOOLS%\aapt2.exe" link -o "%APP_DIR%\build\intermediates\resources.ap_" -I "%PLATFORM%\android.jar" --manifest "%APP_DIR%\src\main\AndroidManifest.xml" --java "%APP_DIR%\build\intermediates" "%APP_DIR%\build\intermediates\res.zip"
if errorlevel 1 (
    echo 资源链接失败
    exit /b 1
)

echo [3/6] 正在编译Java代码...
if not exist "%APP_DIR%\build\classes" mkdir "%APP_DIR%\build\classes"

:: 查找所有java文件
set "JAVA_FILES="
for /r "%APP_DIR%\src\main\java" %%f in (*.java) do (
    set "JAVA_FILES=!JAVA_FILES! "%%f""
)

:: 编译Java - 使用E盘的Android Studio中的jbr
"E:\android\jbr\bin\javac.exe" -encoding UTF-8 -cp "%PLATFORM%\android.jar;%APP_DIR%\build\intermediates" -d "%APP_DIR%\build\classes" %JAVA_FILES%
if errorlevel 1 (
    echo Java编译失败
    exit /b 1
)

echo [4/6] 正在转换为DEX...
"%BUILD_TOOLS%\d8.bat" --release --output "%APP_DIR%\build\intermediates" "%APP_DIR%\build\classes\com\example\albumcleaner\*.class"
if errorlevel 1 (
    echo DEX转换失败
    exit /b 1
)

echo [5/6] 正在打包APK...
copy /Y "%APP_DIR%\build\intermediates\resources.ap_" "%APP_DIR%\build\outputs\app-unsigned.apk"
cd "%APP_DIR%\build\intermediates"
"%BUILD_TOOLS%\aapt.exe" add "%APP_DIR%\build\outputs\app-unsigned.apk" classes.dex
cd "%PROJECT_DIR%"
if errorlevel 1 (
    echo APK打包失败
    exit /b 1
)

echo [6/6] 正在签名APK...
if not exist "%APP_DIR%\build\keystore.jks" (
    echo 正在生成签名密钥...
    "E:\android\jbr\bin\keytool.exe" -genkey -v -keystore "%APP_DIR%\build\keystore.jks" -alias albumcleaner -keyalg RSA -keysize 2048 -validity 10000 -storepass 123456 -keypass 123456 -dname "CN=AlbumCleaner"
)

"%BUILD_TOOLS%\apksigner.bat" sign --ks "%APP_DIR%\build\keystore.jks" --ks-pass pass:123456 --key-pass pass:123456 --out "%APP_DIR%\build\outputs\app-release.apk" "%APP_DIR%\build\outputs\app-unsigned.apk"
if errorlevel 1 (
    echo APK签名失败
    exit /b 1
)

echo.
echo =========================================
echo 构建成功！
echo APK路径: %APP_DIR%\build\outputs\app-release.apk
echo =========================================
