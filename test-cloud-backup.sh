#!/bin/bash -eu
#
# ./test-cloud.backup.sh app.pachli.current
#
# - Ensures cloud backups are enabled
# - Triggers a backup, waits for it to complete
# - Fetches the APK from the device
# - Deletes the app from the device
# - Reinstalls the app using the downloaded APK
# 
# Then you should open the app and check data has been restored.
#
# See https://developer.android.com/identity/data/testingbackup

: "${1?"Usage: $0 package name"}"

# Initialize and create a backup
adb shell bmgr enable true
adb shell bmgr transport com.android.localtransport/.LocalTransport | grep -q "Selected transport" || (echo "Error: error selecting local transport"; exit 1)
adb shell settings put secure backup_local_transport_parameters 'is_encrypted=true'
adb shell bmgr backupnow "$1" | grep -F "Package $1 with result: Success" || (echo "Backup failed"; exit 1)

# Uninstall and reinstall the app to clear the data and trigger a restore
apk_path_list=$(adb shell pm path "$1")
OIFS=$IFS
IFS=$'\n'
apk_number=0
for apk_line in $apk_path_list
do
    (( ++apk_number ))
    apk_path=${apk_line:8:1000}
    adb pull "$apk_path" "myapk${apk_number}.apk"
done
IFS=$OIFS
adb shell pm uninstall --user 0 "$1"
apks=$(seq -f 'myapk%.f.apk' 1 $apk_number)
adb install-multiple -t --user 0 $apks

# Clean up
adb shell bmgr transport com.google.android.gms/.backup.BackupTransportService
rm $apks

echo "Done"
