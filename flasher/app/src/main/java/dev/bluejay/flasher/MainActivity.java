package dev.bluejay.flasher;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Writes a recovery image to bluejay's boot or vendor_boot partition. */
public class MainActivity extends Activity {

    private static final int REQUEST_PICK_IMAGE = 1;
    private static final String BY_NAME = "/dev/block/by-name/";
    private static final String REPO_URL = "https://github.com/irvenhill777/Custom-Recovery-Builder";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private TextView deviceInfo;
    private TextView rootStatus;
    private TextView imageInfo;
    private TextView status;
    private TextView fastbootInfo;
    private TextView adbInfo;
    private RadioButton radioBoot;
    private RadioButton radioVendorBoot;
    private RadioButton slotA;
    private RadioButton slotB;
    private CheckBox confirm;
    private Button pickButton;
    private Button flashButton;
    private Button rebootRecovery;
    private Button rebootSystem;
    private ProgressBar progress;
    private TextView progressLabel;
    private View pageFlash;
    private View pageBuilds;
    private Button tabFlash;
    private Button tabBuilds;

    private Uri imageUri;
    private BootImage image;
    private boolean rooted;
    private String adbCommand = "adb devices";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceInfo = findViewById(R.id.device_info);
        rootStatus = findViewById(R.id.root_status);
        imageInfo = findViewById(R.id.image_info);
        status = findViewById(R.id.status);
        fastbootInfo = findViewById(R.id.fastboot_info);
        adbInfo = findViewById(R.id.adb_info);
        radioBoot = findViewById(R.id.radio_boot);
        radioVendorBoot = findViewById(R.id.radio_vendor_boot);
        slotA = findViewById(R.id.slot_a);
        slotB = findViewById(R.id.slot_b);
        confirm = findViewById(R.id.confirm_flash);
        pickButton = findViewById(R.id.button_pick);
        flashButton = findViewById(R.id.button_flash);
        rebootRecovery = findViewById(R.id.button_reboot_recovery);
        rebootSystem = findViewById(R.id.button_reboot_system);
        progress = findViewById(R.id.progress);
        progressLabel = findViewById(R.id.progress_label);
        pageFlash = findViewById(R.id.page_flash);
        pageBuilds = findViewById(R.id.page_builds);
        tabFlash = findViewById(R.id.tab_flash);
        tabBuilds = findViewById(R.id.tab_builds);

        pickButton.setOnClickListener(v -> pickImage());
        flashButton.setOnClickListener(v -> onFlashClicked());
        rebootRecovery.setOnClickListener(v -> reboot("recovery"));
        rebootSystem.setOnClickListener(v -> reboot(""));
        findViewById(R.id.button_copy).setOnClickListener(v -> copyCommands());
        findViewById(R.id.button_dev_options).setOnClickListener(v -> openDeveloperOptions());
        findViewById(R.id.button_adb_usb).setOnClickListener(v -> enableAdbUsb());
        findViewById(R.id.button_adb_wifi).setOnClickListener(v -> enableAdbWifi());
        findViewById(R.id.button_copy_adb).setOnClickListener(v -> copyAdbCommand());
        findViewById(R.id.button_bootloader).setOnClickListener(v -> rebootBootloader());

        tabFlash.setOnClickListener(v -> showTab(true));
        tabBuilds.setOnClickListener(v -> showTab(false));

        findViewById(R.id.button_open_twrp).setOnClickListener(v -> openUrl(REPO_URL + "/actions/workflows/Recovery-Builder-TWRP.yml"));
        findViewById(R.id.button_open_pbrp).setOnClickListener(v -> openUrl(REPO_URL + "/actions/workflows/Recovery-Builder-PBRP.yml"));
        findViewById(R.id.button_open_apk).setOnClickListener(v -> openUrl(REPO_URL + "/actions/workflows/Build-Flasher-APK.yml"));
        findViewById(R.id.button_open_releases).setOnClickListener(v -> openUrl(REPO_URL + "/releases"));

        ((TextView) findViewById(R.id.builds_info)).setText(
                REPO_URL.replace("https://", "")
                        + "\n\nEach build runs on GitHub Actions. Download the\n"
                        + "image from the run, then flash it from the Flash tab.");

        showTab(true);

        RadioGroup.OnCheckedChangeListener targetChanged = (group, checkedId) -> renderFastboot();
        ((RadioGroup) findViewById(R.id.partition_group)).setOnCheckedChangeListener(targetChanged);
        ((RadioGroup) findViewById(R.id.slot_group)).setOnCheckedChangeListener(targetChanged);

        renderDevice();
        renderFastboot();
        renderAdb();
        checkRoot();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void renderDevice() {
        boolean bluejay = "bluejay".equalsIgnoreCase(Build.DEVICE);
        String line = "Device: " + Build.MODEL + " (" + Build.DEVICE + ")";
        if (!bluejay) line += "\nNot a Pixel 6a — this flasher targets bluejay only.";
        deviceInfo.setText(line);
    }

    private void checkRoot() {
        rootStatus.setText("Root: checking…");
        worker.execute(() -> {
            RootShell.Result id = RootShell.run("id");
            boolean ok = id.exitCode == 0 && id.output.contains("uid=0");
            String suffix = ok ? RootShell.run("getprop ro.boot.slot_suffix").output.trim() : "";
            ui.post(() -> {
                rooted = ok;
                rootStatus.setText(ok
                        ? "Root: available — the app can back up and flash directly."
                        : "Root: not available — use the fastboot commands below from a PC.");
                if ("_b".equals(suffix)) slotB.setChecked(true);
                else if ("_a".equals(suffix)) slotA.setChecked(true);
            });
        });
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            imageUri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
            image = null;
            inspectImage();
        }
    }

    private void inspectImage() {
        imageInfo.setText("Reading image header…");
        status.setText("");
        worker.execute(() -> {
            try {
                BootImage info = BootImage.inspect(getContentResolver(), imageUri);
                info.displayName = displayName(imageUri);
                ui.post(() -> {
                    image = info;
                    renderImage();
                    renderFastboot();
                });
            } catch (Exception e) {
                ui.post(() -> {
                    image = null;
                    imageInfo.setText("Could not read the image: " + message(e));
                });
            }
        });
    }

    private void renderImage() {
        imageInfo.setText(image.displayName + "\n" + image.kindLabel() + " · " + humanSize(image.size));
        if (image.kind == BootImage.Kind.VENDOR_BOOT) radioVendorBoot.setChecked(true);
        else if (image.kind == BootImage.Kind.BOOT) radioBoot.setChecked(true);
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null ? "recovery.img" : last;
    }

    private void onFlashClicked() {
        if (!rooted) {
            status.setText("Root is required to write a partition.\nUse the fastboot commands below from a PC instead.");
            return;
        }
        if (imageUri == null || image == null) {
            status.setText("Choose a recovery image first.");
            return;
        }
        if (!confirm.isChecked()) {
            status.setText("Tick the confirmation box before flashing.");
            return;
        }
        flash();
    }

    private void flash() {
        final String partition = partitionName();
        final String slot = slotName();
        final String target = BY_NAME + partition + "_" + slot;
        final Uri source = imageUri;
        final String name = image.displayName;

        setBusy(true);
        progress.setIndeterminate(false);
        progress.setProgress(0);
        progressLabel.setVisibility(View.VISIBLE);
        showStage(5, "Step 1 of 5 · Staging " + name);
        status.setText("Starting…");

        worker.execute(() -> {
            try {
                post("Staging " + name + "…");
                File staged = stage(source);
                String localHash = sha256(staged);

                showStage(20, "Step 2 of 5 · Checking " + target);
                post("Checking " + target + "…");
                RootShell.Result probe = RootShell.run("test -e " + target + " && echo present");
                if (probe.exitCode != 0 || !probe.output.contains("present")) {
                    throw new IllegalStateException("Partition " + target + " does not exist on this device.");
                }

                String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
                File cachedBackup = new File(getCacheDir(), "backup-" + partition + "_" + slot + "-" + stamp + ".img");

                showStage(30, "Step 3 of 5 · Backing up " + target);
                post("Backing up " + target + "…");
                RootShell.Result backupResult = RootShell.run(
                        "dd if=" + target + " of='" + cachedBackup.getAbsolutePath() + "' bs=1M"
                                + " && chmod 644 '" + cachedBackup.getAbsolutePath() + "'");
                if (backupResult.exitCode != 0) {
                    throw new IllegalStateException("Backup failed: " + tail(backupResult.output));
                }
                if (!cachedBackup.isFile() || cachedBackup.length() == 0) {
                    throw new IllegalStateException("Backup file is empty — stopping before anything is written.");
                }

                File backup = new File(backupDir(), cachedBackup.getName());
                copy(cachedBackup, backup);
                cachedBackup.delete();

                showStage(60, "Step 4 of 5 · Writing " + name + " to " + target);
                post("Writing " + name + " to " + target + "…");
                RootShell.Result write = RootShell.run("dd if='" + staged.getAbsolutePath() + "' of=" + target + " bs=1M conv=fsync");
                if (write.exitCode != 0) {
                    throw new IllegalStateException("Write failed: " + tail(write.output));
                }

                showStage(85, "Step 5 of 5 · Verifying the written bytes");
                post("Verifying the written bytes…");
                RootShell.Result verify = RootShell.run("head -c " + staged.length() + " " + target + " | sha256sum");
                if (verify.exitCode != 0) {
                    throw new IllegalStateException("Verification command failed: " + tail(verify.output));
                }
                String deviceHash = firstWord(verify.output);
                if (!localHash.equals(deviceHash)) {
                    throw new IllegalStateException("Verification failed (device " + deviceHash + ", image " + localHash + ").\nDo not reboot — restore " + backup.getAbsolutePath() + " with dd.");
                }

                showStage(100, "Done · " + partition + "_" + slot + " flashed and verified");
                post("Flashed " + partition + "_" + slot + " (" + humanSize(staged.length()) + ").\nVerified sha256: " + localHash + "\nBackup: " + backup.getAbsolutePath());
                ui.post(() -> {
                    rebootRecovery.setVisibility(View.VISIBLE);
                    rebootSystem.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                ui.post(() -> progressLabel.setText("Stopped · flash failed — see details below"));
                post("Flash stopped: " + message(e));
            } finally {
                ui.post(() -> {
                    pickButton.setEnabled(true);
                    flashButton.setEnabled(true);
                    confirm.setEnabled(true);
                });
            }
        });
    }

    private File stage(Uri uri) throws Exception {
        File staged = new File(getCacheDir(), "staged-recovery.img");
        try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(staged)) {
            if (in == null) throw new IllegalStateException("The selected image could not be opened.");
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        }
        return staged;
    }

    private File backupDir() throws Exception {
        File dir = getExternalFilesDir(null);
        if (dir == null) dir = getFilesDir();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Cannot create backup directory " + dir);
        return dir;
    }

    private void copy(File from, File to) throws Exception {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        }
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format(Locale.US, "%02x", b));
        return hex.toString();
    }

    private void reboot(String target) {
        setBusy(true);
        post("Rebooting" + (target.isEmpty() ? "…" : " to " + target + "…"));
        worker.execute(() -> {
            RootShell.run("reboot " + target);
            ui.post(() -> setBusy(false));
        });
    }

    private void showTab(boolean flash) {
        pageFlash.setVisibility(flash ? View.VISIBLE : View.GONE);
        pageBuilds.setVisibility(flash ? View.GONE : View.VISIBLE);
        tabFlash.setAlpha(flash ? 1f : 0.55f);
        tabBuilds.setAlpha(flash ? 0.55f : 1f);
        tabFlash.setSelected(flash);
        tabBuilds.setSelected(!flash);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "No app can open " + url, Toast.LENGTH_LONG).show();
        }
    }

    private String partitionName() { return radioVendorBoot.isChecked() ? "vendor_boot" : "boot"; }
    private String slotName() { return slotB.isChecked() ? "b" : "a"; }

    private void renderFastboot() {
        String fileName = image == null ? "recovery.img" : image.displayName;
        fastbootInfo.setText("adb reboot bootloader\nfastboot flash " + partitionName() + "_" + slotName()
                + " \"" + fileName + "\"\nfastboot reboot recovery\n\n"
                + "bluejay has no recovery partition: Android 12 images are\n"
                + "vendor_boot, Android 13+ images are boot. Match the image you\n"
                + "picked to the right partition above.");
    }

    private void copyCommands() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("fastboot", fastbootInfo.getText()));
        Toast.makeText(this, "Fastboot commands copied", Toast.LENGTH_SHORT).show();
    }

    private void openDeveloperOptions() {
        startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
    }

    private void enableAdbUsb() {
        if (!rooted) {
            status.setText("Root is required to toggle ADB from the app.\nEnable USB debugging by hand in developer options.");
            return;
        }
        setBusy(true);
        post("Enabling ADB over USB…");
        worker.execute(() -> {
            RootShell.Result result = RootShell.run("settings put global adb_enabled 1 && setprop persist.sys.usb.config adb,mtp && echo enabled");
            boolean ok = result.exitCode == 0 && result.output.contains("enabled");
            ui.post(() -> {
                adbCommand = "adb devices";
                renderAdb();
                setBusy(false);
                status.setText(ok ? "ADB over USB enabled.\nPlug the phone into a PC and run:\nadb devices" : "Could not enable ADB over USB:\n" + tail(result.output));
            });
        });
    }

    private void enableAdbWifi() {
        if (!rooted) {
            status.setText("Root is required to start ADB over Wi-Fi from the app.");
            return;
        }
        setBusy(true);
        post("Starting ADB over Wi-Fi…");
        worker.execute(() -> {
            RootShell.Result result = RootShell.run("setprop service.adb.tcp.port 5555 && stop adbd && start adbd && echo enabled");
            boolean ok = result.exitCode == 0 && result.output.contains("enabled");
            String address = wifiAddress();
            String command = address == null ? "adb connect <phone-ip>:5555" : "adb connect " + address + ":5555";
            ui.post(() -> {
                adbCommand = command;
                renderAdb();
                setBusy(false);
                if (ok) status.setText("ADB over Wi-Fi listening on port 5555.\nOn a PC: " + command
                        + (address == null ? "\nPhone IP could not be read — check the Wi-Fi connection." : ""));
                else status.setText("Could not start ADB over Wi-Fi:\n" + tail(result.output));
            });
        });
    }

    private void copyAdbCommand() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("adb", adbCommand));
        Toast.makeText(this, "ADB command copied", Toast.LENGTH_SHORT).show();
    }

    private void rebootBootloader() {
        if (!rooted) {
            status.setText("Root is required to reboot from the app.\nFrom a PC: adb reboot bootloader");
            return;
        }
        setBusy(true);
        post("Rebooting to bootloader…");
        worker.execute(() -> {
            RootShell.run("reboot bootloader");
            ui.post(() -> setBusy(false));
        });
    }

    private void renderAdb() {
        adbInfo.setText(adbCommand + "\n\nUSB debugging must be on in developer options for\nadb to see the phone. Wireless ADB uses port 5555.");
    }

    private static String wifiAddress() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || !nif.getName().startsWith("wlan")) continue;
                for (InterfaceAddress address : nif.getInterfaceAddresses()) {
                    InetAddress inet = address.getAddress();
                    if (inet instanceof Inet4Address && !inet.isLoopbackAddress()) return inet.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void setBusy(boolean busy) {
        if (busy) {
            progress.setIndeterminate(true);
            progress.setProgress(0);
            progress.setVisibility(View.VISIBLE);
        } else {
            progress.setVisibility(View.GONE);
            progressLabel.setVisibility(View.GONE);
        }
        pickButton.setEnabled(!busy);
        flashButton.setEnabled(!busy);
        confirm.setEnabled(!busy);
    }

    private void showStage(int percent, String label) {
        ui.post(() -> {
            progress.setIndeterminate(false);
            progress.setProgress(percent);
            progressLabel.setVisibility(View.VISIBLE);
            progressLabel.setText(label);
        });
    }

    private void post(String text) { ui.post(() -> status.setText(text)); }

    private static String firstWord(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return "";
        return trimmed.split("\\s+")[0].toLowerCase(Locale.US);
    }

    private static String tail(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return "no output";
        String[] lines = trimmed.split("\n");
        return lines[lines.length - 1];
    }

    private static String humanSize(long bytes) {
        if (bytes >= 1024L * 1024L) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
    }

    private static String message(Exception e) {
        String text = e.getMessage();
        return text == null || text.isEmpty() ? e.toString() : text;
    }
}
