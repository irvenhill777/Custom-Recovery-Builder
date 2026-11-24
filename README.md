## Custom Recovery Builder
Compile your first custom recovery via Github Actions - with ldcheck setup. This workflow supports both TWRP and PBRP, you can use it with other custom recovery as well, but you have to modify the yaml config yourself.

# How to Use
1. Fork this repository.

2. Go to `Action` tab > `All workflows` > Pick which Build you need (`TWRP or PBRP`) > `Run workflow`, then pick required information from each drop-down list:
 * Manifest Branch (*12.1, *11.0.)
 * Device Tree (Your device tree repository link)
 * Device Tree Branch (Your device tree repository branch)
 * Device Name (Your device codename)
 * Device Path (device/brand/codename)
 * Build Target (boot, reecovery, vendorboot)
 * LDCHECK (path to your target binary file, ie. `system/bin/qseecomd`)
   - If you are building manually/locally and you want to use ldcheck for checking dependencies, visit [THIS](https://github.com/TeamWin/android_device_qcom_twrp-common/tree/android-11#using-ldcheck-to-find-dependencies) this for guide.
  
## Flasher APK (Pixel 6a / bluejay)

`flasher/` is a small Android app that writes a recovery image straight to the Pixel 6a's `boot` or `vendor_boot` partition on a rooted device, and shows the equivalent fastboot commands for everything else.

1. Go to `Actions` > `Build Flasher APK` > `Run workflow` and pick `debug` (installable) or `release`.
2. Download the `bluejay-recovery-flasher-*` artifact from the run and install the APK.
3. Choose a recovery image (TWRP or PBRP built by this repo, or any other `.img`), pick the partition and slot, confirm, and flash. The app backs up the current partition first and verifies the written bytes with sha256.

The app has two tabs: **Flash** for writing an image (with USB debugging helpers: developer options, ADB over USB or Wi-Fi, `adb connect`, reboot to bootloader) and **Builds** with links to the TWRP, PBRP and APK workflow runs.

bluejay has no separate recovery partition: Android 12 images live in `vendor_boot`, Android 13+ images in `boot`. The app reads the image header and preselects the matching partition.

## Notes
   - Initially, this workflow only supports up to 4 branches for manifest 11 and 12.1, if you build below that, add it yourself.

## Credits
- https://github.com/that1
- https://github.com/TeamWin
- https://github.com/PitchBlackRecoveryProject
- https://github.com/azwhikaru
- And to all Contributors in every repositories and scripts I used.
