<p align="center"><img src="docs/icon.png" width="128" alt="TV Folder Picker icon"></p>

# TV Folder Picker

A tiny open-source app that fixes the **"no app to open" / "You don't have an app that can do this"** error when an app on your **Android TV** asks you to pick a folder.

Many Android TV builds ship without Android's system file and folder picker (DocumentsUI). When an app (an emulator, a media player, a backup tool, and so on) asks you to choose a folder, there is nothing to answer the request, so you get an error. TV Folder Picker answers that request with a simple remote-friendly folder browser.

> **Status:** works on the author's Xiaomi Android TV (Android TV 14). Other devices are untested, and reports are welcome.

## Are you affected?

Run this from a PC with `adb`:

```
adb shell cmd package query-activities -a android.intent.action.OPEN_DOCUMENT_TREE
```

If the only result is `com.android.tv.frameworkpackagestubs` (a placeholder package with no folder browser), your TV has no folder picker and this app should help.

## Install

You need a PC with [`adb`](https://developer.android.com/tools/releases/platform-tools) and ADB debugging enabled on the TV (Settings > Device Preferences > About: press *Build* seven times, then turn on USB or network debugging in Developer options).

1. Download the APK from the [Releases](../../releases) page.
2. Connect and install:
   ```
   adb connect <tv-ip-address>:5555
   adb install -r TV-FolderPicker0.1.apk
   ```
3. Grant "All files access". TVs usually have no settings screen for this, so use adb:
   ```
   adb shell appops set com.tvpicker.shim MANAGE_EXTERNAL_STORAGE allow
   ```
4. Check that the picker is registered:
   ```
   adb shell cmd package query-activities -a android.intent.action.OPEN_DOCUMENT_TREE
   ```
   The output should now include `com.tvpicker.shim/.PickerActivity`.
5. **Only if needed:** if apps still say there is no app to open, disable the placeholder so it can't answer first. This is reversible.
   ```
   adb shell pm disable-user --user 0 com.android.tv.frameworkpackagestubs
   ```
   To undo it: `adb shell pm enable com.android.tv.frameworkpackagestubs`

## Use

Open the folder-selection function in any app. TV Folder Picker appears:

- **Up / Down** moves through the list, **OK** opens a folder.
- Choose **[ SELECT THIS FOLDER ]** to select the folder you are in.
- **[ .. ] Up** or the **Back** button goes up one level. Back on the storage list cancels.
- Internal storage and USB drives are listed on the first screen.

You can also open **TV Folder Picker** from the app list to see whether "All files access" is granted.

## Limitations

- **Folder selection only.** It answers `OPEN_DOCUMENT_TREE`. It does not handle single-file pickers (`OPEN_DOCUMENT`, `GET_CONTENT`) or "save as" (`CREATE_DOCUMENT`). Keep a file manager installed for those.
- **Android 14 still blocks `Android/data` and `Android/obb`** for ordinary apps, even with all files access, so those folders can look empty or unreadable.
- Some apps look for specific picker apps instead of just sending the request. Those may still complain.
- It is unofficial software tested on one device. Use it at your own risk.

## Privacy and permissions

- The only permission it asks for is **All files access** (`MANAGE_EXTERNAL_STORAGE`), which it needs to browse and serve folders.
- It has **no internet permission**, no analytics and no ads. It cannot send your data anywhere.
- When you pick a folder, only that folder (and what is inside it) is shared with the requesting app.
- Its document provider requires the system `MANAGE_DOCUMENTS` permission, so other apps cannot read your files through it without a folder grant from you.

## How it works

The app contains two parts:

1. `PickerActivity` answers `ACTION_OPEN_DOCUMENT_TREE` and shows a D-pad friendly folder list.
2. `FsProvider` is a `DocumentsProvider` that exposes your storage volumes. When you choose a folder, the picker returns a tree URI for it with read, write and persistable permission grants, which is what apps expect from the system picker.

Every path the provider serves is checked to be inside a mounted storage volume.

## Uninstall

```
adb uninstall com.tvpicker.shim
adb shell pm enable com.android.tv.frameworkpackagestubs
```

## Troubleshooting

| Problem | Try this |
|---|---|
| `query-activities` doesn't list `com.tvpicker.shim` | Reinstall the APK and run the command again. |
| App still says "no app to open" | Do install step 5 (disable the placeholder). |
| A folder looks empty | Check that all files access is granted (open the app), and remember `Android/data` is blocked on Android 14. |
| `adb connect` fails | Turn on network debugging on the TV and check the IP address in the network settings. |
| An app still reports "no document browser" | Open an issue with the app's name. It may be checking for a specific picker. |

## Build from source

You need JDK 17, the Android SDK (platform 34 and build-tools 36.0.0), and Gradle 9.6 or newer (the project uses Android Gradle Plugin 9.5.0).

```
set ANDROID_HOME=C:\path\to\Android
gradle assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Background

Apps are meant to use Android's Storage Access Framework for folder selection, which relies on the device providing a system picker. Some Android TV devices don't include one. This has been discussed for years but google team (Billion $ company!!)refuse to fix it, for example in [Storage Access Framework, Missing in Action](https://commonsware.com/blog/2017/12/27/storage-access-framework-missing-action.html and https://issuetracker.google.com/issues/202985150?pli=1).

Feel free to create a fork and customize or add more features to it, I'm not an actual coder, written it with help from Claude (Anthropic) free version of sonnet 5.5.
## License

[MIT](LICENSE)
