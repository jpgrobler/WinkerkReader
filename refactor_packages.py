import os
import re
import shutil

# Mapping of file -> subpackage (domain relative to base package)
# Base package: za.co.jpsoft.winkerkreader
domain_map = {
    # UI: Activities
    "MainActivity.kt": "ui.activities",
    "ArgiefListActivity.kt": "ui.activities",
    "CallLogActivity.kt": "ui.activities",
    "FilterActivity.kt": "ui.activities",
    "LaaiDatabasisActivity.kt": "ui.activities",
    "LidmaatDetailActivity.kt": "ui.activities",
    "PermissionsActivity.kt": "ui.activities",
    "RegistreerActivity.kt": "ui.activities",
    "SettingsActivity.kt": "ui.activities",
    "SplashActivity.kt": "ui.activities",
    "UitlegActivity.kt": "ui.activities",
    "VerjaarSmsActivity.kt": "ui.activities",
    
    # UI: ViewModels
    "ArgiefViewModel.kt": "ui.viewmodels",
    "EventViewModel.kt": "ui.viewmodels",
    "LidmaatDetailViewModel.kt": "ui.viewmodels",
    "MemberViewModel.kt": "ui.viewmodels",
    
    # UI: Adapters
    "CallLogAdapter.kt": "ui.adapters",
    "SpinnerAdapter.kt": "ui.adapters",
    "WinkerkCursorAdapter.kt": "ui.adapters",
    
    # UI: Components
    "FilterCheckBox.kt": "ui.components",
    "ListViewSelected.kt": "ui.components",
    "SearchCheckBox.kt": "ui.components",
    "WellBehavedEditText.kt": "ui.components",
    
    # Data
    "DatabaseHelper.kt": "data",
    "WinkerkContract.kt": "data",
    "WinkerkDbHelper.kt": "data",
    "WinkerkProvider.kt": "data",
    
    # Data: Models
    "FilterBox.kt": "data.models",
    "CallLog.kt": "data.models",
    "CallRecord.kt": "data.models",
    "CallType.kt": "data.models",
    "CalendarInfo.kt": "data.models",
    "SmsList.kt": "data.models",
    
    # Services
    "CallMonitoringService.kt": "services",
    "MyService.kt": "services",
    "OproepDetailService.kt": "services",
    "WhatsAppNotificationService.kt": "services",
    "ListViewWidgetService.kt": "services",
    
    # Receivers
    "AlarmReceiver.kt": "services.receivers",
    "DeviceBootReceiver.kt": "services.receivers",
    "IncomingCall.kt": "services.receivers",
    
    # Workers
    "BirthdayReminderWorker.kt": "workers",
    "DropboxDownloadWorker.kt": "workers",
    "PhotoDownloadWorker.kt": "workers",
    "WidgetRefreshWorker.kt": "workers",
    
    # Widget
    "WinkerkReaderWidgetProvider.kt": "widget",
    "WidgetQueryBuilder.kt": "widget",
    
    # Root Core App
    "WinkerkReader.kt": "",
}

# The rest fall into `utils`
base_kt_dir = r"c:\Pieter Folders\WinkerkReader\Winkerk10Reader 2024\app\src\main\kotlin"
base_package = "za.co.jpsoft.winkerkreader"
new_base_path = os.path.join(base_kt_dir, "za", "co", "jpsoft", "winkerkreader")

# Get list of all KT files
all_kt_files = [f for f in os.listdir(base_kt_dir) if f.endswith(".kt") and os.path.isfile(os.path.join(base_kt_dir, f))]

# Assign defaults to utils if not found
for f in all_kt_files:
    if f not in domain_map:
        domain_map[f] = "utils"

# Map exactly which class belongs to which full package for import tracking
class_to_full_package = {}
for filename, subpkg in domain_map.items():
    # Attempt to derive primary class name from filename (e.g. MainActivity.kt -> MainActivity)
    class_name = os.path.splitext(filename)[0]
    full_pkg = f"{base_package}.{subpkg}" if subpkg else base_package
    class_to_full_package[class_name] = full_pkg

# Add secondary classes manually that are exported
class_to_full_package["CallLogViewHolder"] = f"{base_package}.ui.adapters"
class_to_full_package["AppSessionState"] = f"{base_package}.utils"
class_to_full_package["SettingsManager"] = f"{base_package}.utils"


# 1. Create Directories and Move Files
print("Moving files to new directories...")
for filename, subpkg in domain_map.items():
    old_path = os.path.join(base_kt_dir, filename)
    
    # Create the internal hierarchy
    if subpkg:
        target_dir = os.path.join(new_base_path, *subpkg.split("."))
    else:
        target_dir = new_base_path
        
    os.makedirs(target_dir, exist_ok=True)
    new_path = os.path.join(target_dir, filename)
    
    if os.path.exists(old_path):
        shutil.move(old_path, new_path)

# 2. Update Packages and Imports in KT Files
print("Updating Kotlin files...")
for root, _, files in os.walk(new_base_path):
    for filename in files:
        if filename.endswith(".kt"):
            filepath = os.path.join(root, filename)
            
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()

            # What is this file's current domain subpackage?
            subpkg = domain_map.get(filename, "utils")
            my_full_pkg = f"{base_package}.{subpkg}" if subpkg else base_package
            
            # Step A: Update the package declaration
            # Sometimes it's already structured incorrectly or missing.
            old_pkg_regex = re.compile(r"^package\s+za\.co\.jpsoft\.winkerkreader(?:\.data)?", re.MULTILINE)
            # Remove existing package declaration
            content = old_pkg_regex.sub("", content).lstrip()
            # Inject new one at the very top
            content = f"package {my_full_pkg}\n\n" + content
            
            # Step B: Auto-detect used classes and add imports if they differ from my_full_pkg
            imports_to_add = set()
            for c_name, c_pkg in class_to_full_package.items():
                if c_name in content and c_pkg != my_full_pkg:
                    # Ignore if the class name is a substring of something larger, etc.
                    # Basic check via regex \bClassName\b
                    if re.search(r"\b" + re.escape(c_name) + r"\b", content):
                        imports_to_add.add(f"import {c_pkg}.{c_name}")
            
            if imports_to_add:
                # Find the last import, or just inject after package
                # Simple injection:
                import_block = "\n".join(imports_to_add)
                # find package declaration line to insert imports below
                pkg_match = re.search(r"^package .*\n+", content, re.MULTILINE)
                if pkg_match:
                    insert_idx = pkg_match.end()
                    content = content[:insert_idx] + import_block + "\n\n" + content[insert_idx:]
                else:
                    content = import_block + "\n\n" + content
            
            # Additional cleanup: Old explicit data imports like za.co.jpsoft.winkerkreader.data.* might hang around.
            content = re.sub(r"import za\.co\.jpsoft\.winkerkreader\.data\..*\n", "", content)
            
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(content)

# 3. Update AndroidManifest.xml
print("Updating AndroidManifest.xml...")
manifest_path = r"c:\Pieter Folders\WinkerkReader\Winkerk10Reader 2024\app\src\main\AndroidManifest.xml"
with open(manifest_path, 'r', encoding='utf-8') as f:
    manifest = f.read()

# Replace shorthand '.ActivityName' -> '.ui.activities.ActivityName'
for filename, subpkg in domain_map.items():
    if not subpkg: continue
    cname = os.path.splitext(filename)[0]
    
    # Services, receivers, activities
    manifest = re.sub(r'android:name="\.' + re.escape(cname) + r'"', f'android:name=".{subpkg}.{cname}"', manifest)

with open(manifest_path, 'w', encoding='utf-8') as f:
    f.write(manifest)

# 4. Update XML layouts (tools:context, custom views)
print("Updating XML Layouts...")
res_dir = r"c:\Pieter Folders\WinkerkReader\Winkerk10Reader 2024\app\src\main\res"
for root, _, files in os.walk(res_dir):
    for filename in files:
        if filename.endswith(".xml"):
            filepath = os.path.join(root, filename)
            with open(filepath, 'r', encoding='utf-8') as f:
                xml = f.read()
            
            changed = False
            for fname, subpkg in domain_map.items():
                if not subpkg: continue
                cname = os.path.splitext(fname)[0]
                
                # tools:context=".MainActivity" -> tools:context=".ui.activities.MainActivity"
                new_xml = re.sub(r'tools:context="\.' + re.escape(cname) + r'"', f'tools:context=".{subpkg}.{cname}"', xml)
                
                # Custom views: <za.co.jpsoft.winkerkreader.WellBehavedEditText -> <za...ui.components.WellBehavedEditText
                new_xml = re.sub(r'<za\.co\.jpsoft\.winkerkreader\.' + re.escape(cname), f'<{base_package}.{subpkg}.{cname}', new_xml)
                
                if new_xml != xml:
                    xml = new_xml
                    changed = True
                    
            if changed:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(xml)

print("Restructure complete!")
