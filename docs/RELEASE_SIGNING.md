# Local Release signing

Run `scripts/build-release.ps1` from PowerShell. It uses a persistent, randomly protected RSA-4096 signing key and verifies the generated APK with Android SDK `apksigner`. The private key and protected password live in ignored `.signing/`; they are never committed.

For future updates, preserve **both** `secureledger-local-release.p12` and its password. The `.dpapi` password file can only be decrypted by the same Windows account on the same installation. To copy the password into a trusted password manager, run the following locally in a private terminal, then clear terminal history as appropriate:

```powershell
$protected = Get-Content .signing\local-release-password.dpapi -Raw | ConvertTo-SecureString
[System.Net.NetworkCredential]::new("", $protected).Password
```

Keep an encrypted `.sldb` backup of the vault independently of the APK and signing key. If the key is lost, a newly signed APK cannot upgrade this installation; restoring the encrypted backup into a fresh installation is the recovery path. Never commit the keystore, password, or exported backup. A release signed by this local key is installable for direct distribution; store distribution has additional policy and account requirements that have not been validated here.

## Existing Debug-signed installations

Android requires an update to use the same application ID **and signing certificate**. If an older Debug-signed `com.secureledger` remains installed—or its package record and data are retained in another Android user or parallel-space profile—the separately signed Release APK fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, sometimes shown as “incompatible” or “invalid” by a file manager. Changing the Android target version or renaming the APK does not fix this. The current Debug APK can update a matching Debug installation without clearing its data.

To move to the Release signing key, first export and verify an encrypted `.sldb` backup from every vault whose data matters. Only after the backups are secured should the old package be removed from all affected Android users, then the Release APK installed and the backup restored. Package removal can erase local vault data; do not automate it without explicit approval. [Android's update rules](https://developer.android.com/google/play/app-updates) explain the certificate requirement.
