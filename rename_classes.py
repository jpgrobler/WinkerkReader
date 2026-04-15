import os
import re

class_renames = {
    "MainActivity2": "MainActivity",
    "Laaidatabasis": "LaaiDatabasisActivity",
    r"MyActivity_Settings(?!_Adapter)": "SettingsActivity", # negative lookahead to avoid breaking adapter
    "MyActivity_Settings_Adapter": "SettingsAdapter",
    "MyFilter": "FilterActivity",
    "argief_List": "ArgiefListActivity",
    "argiefLysAdapter": "ArgiefLysAdapter",
    "lidmaat_detail_Activity": "LidmaatDetailActivity",
    "oproepdetail": "OproepDetailService",
    "registreer": "RegistreerActivity",
    "winkerkProvider": "WinkerkProvider",
    "winkerk_DB_Helper": "WinkerkDbHelper",
    "VerjaarSMS2": "VerjaarSmsActivity"
}

file_renames = {
    "MainActivity2.kt": "MainActivity.kt",
    "Laaidatabasis.kt": "LaaiDatabasisActivity.kt",
    "MyActivity_Settings.kt": "SettingsActivity.kt",
    "MyFilter.kt": "FilterActivity.kt",
    "argief_List.kt": "ArgiefListActivity.kt",
    "lidmaat_detail_Activity.kt": "LidmaatDetailActivity.kt",
    "oproepdetail.kt": "OproepDetailService.kt",
    "registreer.kt": "RegistreerActivity.kt",
    "winkerkProvider.kt": "WinkerkProvider.kt",
    "winkerk_DB_Helper.kt": "WinkerkDbHelper.kt",
    "FiterHandler.kt": "FilterHandler.kt",
    "VerjaarSMS2.kt": "VerjaarSmsActivity.kt"
}

# Directories to process
kt_dir = r"c:\Pieter Folders\WinkerkReader\Winkerk10Reader 2024\app\src\main\kotlin"
res_dir = r"c:\Pieter Folders\WinkerkReader\Winkerk10Reader 2024\app\src\main\res"
manifest_path = r"c:\Pieter Folders\WinkerkReader\Winkerk10Reader 2024\app\src\main\AndroidManifest.xml"

# Replace within a file
def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
        
    old_content = content
    for old_name, new_name in class_renames.items():
        # Using word boundaries for safety except for regex entries
        if old_name.startswith(r"MyActivity_Settings("):
            content = re.sub(r"\b" + old_name + r"\b", new_name, content)
        else:
            # We want to match whole words to avoid partial matches
            content = re.sub(r"\b" + re.escape(old_name) + r"\b", new_name, content)

    if content != old_content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Updated references in {os.path.basename(filepath)}")

# 1. Update references in Kotlin files
print("Updating KT files...")
for root, dirs, files in os.walk(kt_dir):
    for filename in files:
        if filename.endswith(".kt"):
            process_file(os.path.join(root, filename))

# 2. Update references in XML layout files
print("Updating XML files...")
for root, dirs, files in os.walk(res_dir):
    for filename in files:
        if filename.endswith(".xml"):
            process_file(os.path.join(root, filename))

# 3. Update AndroidManifest.xml
print("Updating AndroidManifest.xml...")
process_file(manifest_path)

# 4. Rename the Kotlin files
print("Renaming physical files...")
for old_name, new_name in file_renames.items():
    old_path = os.path.join(kt_dir, old_name)
    new_path = os.path.join(kt_dir, new_name)
    if os.path.exists(old_path):
        os.rename(old_path, new_path)
        print(f"Renamed {old_name} -> {new_name}")
    else:
        print(f"File {old_name} not found")

print("Done")
