package com.example.albumcleaner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQUEST_MANAGE_ALL_FILES = 1001;
    private static final int REQUEST_STORAGE_PERMISSIONS = 1002;

    private Button btnClear;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvProgress;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 测试 legacy storage 的目录
    private static final String TEST_DIR = "/storage/emulated/0/DCIM/";

    // 需要扫描的目录
    private final String[] TARGET_DIRS = {
            "/storage/emulated/0/DCIM/",
            "/storage/emulated/0/Pictures/",
            "/storage/emulated/0/Tencent/MicroMsg/WeiXin/"
    };

    // 需要处理的文件扩展名
    private final String[] TARGET_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".mp4", ".mov"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        
        // 启动时检测权限，但不自动执行
        checkAndPrepare();
    }

    private void initViews() {
        btnClear = findViewById(R.id.btn_clear);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);
        tvProgress = findViewById(R.id.tv_progress);

        btnClear.setOnClickListener(v -> checkPermissionAndExecute());
    }

    /**
     * 启动时准备：显示当前权限状态
     */
    private void checkAndPrepare() {
        updateStatus("检测存储访问权限...");
        
        executor.execute(() -> {
            // 第一步：测试 legacy storage 是否生效
            if (testLegacyStorage()) {
                mainHandler.post(() -> {
                    updateStatus("✓ Legacy Storage 可用，点击按钮执行安全管控");
                    btnClear.setEnabled(true);
                });
                return;
            }

            // Legacy 失败，检查 MANAGE_EXTERNAL_STORAGE
            mainHandler.post(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        updateStatus("✓ 已获得所有文件访问权限，点击按钮执行安全管控");
                        btnClear.setEnabled(true);
                    } else {
                        updateStatus("需要文件管控权限进行安全防护，点击按钮申请");
                        btnClear.setEnabled(true);
                    }
                } else {
                    // Android 10 及以下
                    if (checkStoragePermissions()) {
                        updateStatus("✓ 已获得存储权限，点击按钮执行安全管控");
                        btnClear.setEnabled(true);
                    } else {
                        updateStatus("需要存储权限进行安全防护，点击按钮申请");
                        btnClear.setEnabled(true);
                    }
                }
            });
        });
    }

    /**
     * 第一步：测试 legacy storage 是否生效
     * 尝试用 File API 直接读取 /storage/emulated/0/DCIM/
     */
    private boolean testLegacyStorage() {
        try {
            File testDir = new File(TEST_DIR);
            if (testDir.exists() && testDir.isDirectory()) {
                File[] files = testDir.listFiles();
                // 如果能列出文件（即使为空数组），说明 legacy storage 生效
                return files != null;
            }
        } catch (SecurityException e) {
            // 权限被拒绝
            return false;
        }
        return false;
    }

    /**
     * 点击按钮后：检查权限并执行
     */
    private void checkPermissionAndExecute() {
        // 第一步：测试 legacy storage
        updateStatus("正在测试 Legacy Storage...");
        
        executor.execute(() -> {
            if (testLegacyStorage()) {
                mainHandler.post(() -> {
                    updateStatus("✓ Legacy Storage 可用");
                    startSecurityControl();
                });
                return;
            }

            // Legacy 失败，进入第二步
            mainHandler.post(() -> {
                updateStatus("✗ Legacy Storage 无效");
                checkAndRequestManageStorage();
            });
        });
    }

    /**
     * 第二步：检查并申请 MANAGE_EXTERNAL_STORAGE
     */
    private void checkAndRequestManageStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                updateStatus("✓ 已获得所有文件访问权限");
                startSecurityControl();
            } else {
                requestManageAllFilesPermission();
            }
        } else {
            // Android 10 及以下使用传统权限
            if (checkStoragePermissions()) {
                updateStatus("✓ 已获得存储权限");
                startSecurityControl();
            } else {
                requestStoragePermissions();
            }
        }
    }

    /**
     * 申请所有文件访问权限 (Android 11+)
     */
    private void requestManageAllFilesPermission() {
        new AlertDialog.Builder(this)
                .setTitle("需要文件管控权限进行安全防护")
                .setMessage("Legacy Storage 在当前系统不可用。\n\n本应用需要文件管控权限才能执行安全防护。请在设置中授予权限。")
                .setPositiveButton("去设置", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    Uri uri = Uri.parse("package:" + getPackageName());
                    intent.setData(uri);
                    startActivityForResult(intent, REQUEST_MANAGE_ALL_FILES);
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    updateStatus("未获得权限，无法进行安全管控");
                    Toast.makeText(this, "需要权限才能继续", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * 检查传统存储权限 (Android 10 及以下)
     */
    private boolean checkStoragePermissions() {
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                        == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 申请传统存储权限
     */
    private void requestStoragePermissions() {
        requestPermissions(
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_STORAGE_PERMISSIONS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_ALL_FILES) {
            // 用户从设置返回，重新检测
            checkAndRequestManageStorage();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateStatus("✓ 已获得存储权限");
                startSecurityControl();
            } else {
                updateStatus("✗ 未获得权限，无法进行安全管控");
                Toast.makeText(this, "需要权限才能继续", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 开始执行安全管控
     */
    private void startSecurityControl() {
        btnClear.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        updateStatus("正在扫描文件...");

        executor.execute(() -> {
            // 1. 扫描所有目标文件
            List<File> filesToProcess = new ArrayList<>();
            for (String dirPath : TARGET_DIRS) {
                File dir = new File(dirPath);
                if (dir.exists() && dir.isDirectory()) {
                    scanDirectory(dir, filesToProcess);
                }
            }

            int totalFiles = filesToProcess.size();
            if (totalFiles == 0) {
                mainHandler.post(() -> {
                    updateStatus("未找到需要处理的文件");
                    btnClear.setEnabled(true);
                    progressBar.setVisibility(View.GONE);
                    tvProgress.setText("");
                });
                return;
            }

            // 2. 逐个删除文件
            int deletedCount = 0;
            int failedCount = 0;
            List<String> processedPaths = new ArrayList<>();

            for (int i = 0; i < filesToProcess.size(); i++) {
                File file = filesToProcess.get(i);
                final int current = i + 1;
                final String fileName = file.getName();

                mainHandler.post(() -> updateProgress(current, totalFiles, fileName));

                // 直接删除本地文件
                if (file.delete()) {
                    deletedCount++;
                    processedPaths.add(file.getAbsolutePath());
                } else {
                    failedCount++;
                }

                // 短暂延迟以便用户看到进度
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // 3. 刷新媒体库
            if (!processedPaths.isEmpty()) {
                refreshMediaLibrary(processedPaths);
            }

            final int finalDeletedCount = deletedCount;
            final int finalFailedCount = failedCount;
            mainHandler.post(() -> {
                updateStatus("✓ 完成！删除成功: " + finalDeletedCount + ", 失败: " + finalFailedCount);
                tvProgress.setText("");
                progressBar.setVisibility(View.GONE);
                btnClear.setEnabled(true);

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("安全管控完成")
                        .setMessage("删除成功: " + finalDeletedCount + " 个文件\n删除失败: " + finalFailedCount + " 个文件")
                        .setPositiveButton("确定", null)
                        .show();
            });
        });
    }

    /**
     * 递归扫描目录
     */
    private void scanDirectory(File dir, List<File> filesToProcess) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, filesToProcess);
            } else if (isTargetFile(file)) {
                filesToProcess.add(file);
            }
        }
    }

    /**
     * 检查文件是否是目标类型
     */
    private boolean isTargetFile(File file) {
        String name = file.getName().toLowerCase();
        for (String ext : TARGET_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 刷新媒体库
     */
    private void refreshMediaLibrary(List<String> paths) {
        String[] pathArray = paths.toArray(new String[0]);
        MediaScannerConnection.scanFile(this, pathArray, null, (path, uri) -> {
            // 扫描完成回调
        });
    }

    /**
     * 更新状态文本
     */
    private void updateStatus(String message) {
        tvStatus.setText(message);
    }

    /**
     * 更新进度显示
     */
    private void updateProgress(int current, int total, String fileName) {
        tvProgress.setText("(" + current + "/" + total + ") " + fileName);
        int progress = (int) ((current * 100.0) / total);
        progressBar.setProgress(progress);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
