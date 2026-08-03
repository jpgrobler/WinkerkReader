import os
import shutil
import re
from pathlib import Path

BASE_DIR = r"c:\Pieter Folders\WinkerkReader\Winkerk10Reader 2024\app\src\main\kotlin"
DATA_DIR = os.path.join(BASE_DIR, "za", "co", "jpsoft", "winkerkreader", "data")
PKG_PREFIX = "za.co.jpsoft.winkerkreader.data"

# (Filename, Source Sub-dir, Target Sub-dir, Target Package suffix)
moves = [
    # Members - setup
    ("DatabaseHelper.kt", "", "members/setup", "members.setup"),
    ("DatabaseInitializer.kt", "", "members/setup", "members.setup"),
    ("WinkerkDatabase.kt", "room", "members/setup", "members.setup"),
    
    # Members - dao
    ("MemberDao.kt", "room", "members/dao", "members.dao"),
    ("ArgiefDao.kt", "room", "members/dao", "members.dao"),
    ("DatumDao.kt", "room", "members/dao", "members.dao"),
    
    # Members - entities
    ("MemberEntity.kt", "room", "members/entities", "members.entities"),
    ("ArchiveEntity.kt", "room", "members/entities", "members.entities"),
    ("DatumEntity.kt", "room", "members/entities", "members.entities"),
    
    # Members - repository
    ("MemberRepository.kt", "", "members/repository", "members.repository"),
    ("ChurchInfoRepository.kt", "repositories", "members/repository", "members.repository"),
    ("ContactRepository.kt", "repositories", "members/repository", "members.repository"),
    
    # Members - queries
    ("MemberPagingSource.kt", "", "members/queries", "members.queries"),
    ("MemberQueryBuilder.kt", "", "members/queries", "members.queries"),
    ("MemberItemSeparator.kt", "", "members/queries", "members.queries"),
    
    # Members - provider
    ("WinkerkContract.kt", "", "members/provider", "members.provider"),
    ("WinkerkProvider.kt", "", "members/provider", "members.provider"),
    
    # Members - models
    ("MemberItem.kt", "models", "members/models", "members.models"),
    ("MemberDetailItem.kt", "models", "members/models", "members.models"),
    ("FamilyMember.kt", "models", "members/models", "members.models"),
    
    # Calllog - models
    ("CallLog.kt", "models", "calllog/models", "calllog.models"),
    ("CallRecord.kt", "models", "calllog/models", "calllog.models"),
    ("CallType.kt", "models", "calllog/models", "calllog.models"),
    
    # Calllog - internal reorganization
    ("CallLogDao.kt", "calllog", "calllog/dao", "calllog.dao"),
    ("CallLogEntity.kt", "calllog", "calllog/entities", "calllog.entities"),
    ("ActiveCallEntity.kt", "calllog", "calllog/entities", "calllog.entities"),
    ("CallLogDatabase.kt", "calllog", "calllog/setup", "calllog.setup"),
    ("CallLogDatabaseBackup.kt", "calllog", "calllog/setup", "calllog.setup"),
    ("CallLogImporter.kt", "calllog", "calllog/setup", "calllog.setup"),
    ("Converters.kt", "calllog", "calllog/setup", "calllog.setup"),
]

# Track imports changes to apply to whole project
# { "ClassName": ("old.package", "new.package") }
import_changes = {}

print("Step 1: Moving files and updating packages...")
for filename, src_sub, dest_sub, dest_pkg_suffix in moves:
    src_dir = os.path.join(DATA_DIR, src_sub) if src_sub else DATA_DIR
    src_path = os.path.join(src_dir, filename)
    
    dest_dir = os.path.join(DATA_DIR, *dest_sub.split('/'))
    dest_path = os.path.join(dest_dir, filename)
    
    old_pkg = f"{PKG_PREFIX}.{src_sub.replace('/', '.')}".strip('.')
    if not src_sub:
        old_pkg = PKG_PREFIX
        
    new_pkg = f"{PKG_PREFIX}.{dest_pkg_suffix}"
    
    if not os.path.exists(src_path):
        print(f"Warning: {src_path} not found. Skipping.")
        continue
        
    # Read content and change package
    with open(src_path, "r", encoding="utf-8") as f:
        content = f.read()
        
    new_content = re.sub(rf"package\s+{old_pkg}(\s|$)", f"package {new_pkg}\n", content)
    
    os.makedirs(dest_dir, exist_ok=True)
    with open(dest_path, "w", encoding="utf-8") as f:
        f.write(new_content)
        
    # Delete original if it's not the same path
    if src_path != dest_path:
        os.remove(src_path)
        
    class_name = os.path.splitext(filename)[0]
    import_changes[class_name] = (old_pkg, new_pkg)
    print(f"Moved {filename} to {dest_sub}")

print("\nStep 2: Updating imports across the project...")
kt_files = list(Path(BASE_DIR).rglob("*.kt"))
for kt_file in kt_files:
    with open(kt_file, "r", encoding="utf-8") as f:
        content = f.read()
        
    original_content = content
    for class_name, (old_pkg, new_pkg) in import_changes.items():
        old_import = f"import {old_pkg}.{class_name}"
        new_import = f"import {new_pkg}.{class_name}"
        content = content.replace(old_import, new_import)
        
        # Also handle wildcard imports (less safe but necessary if used)
        old_wildcard = f"import {old_pkg}.*"
        # If wildcard exists, we might need to add the new specific import
        if old_wildcard in content and new_import not in content:
            content = content.replace(old_wildcard, f"{old_wildcard}\nimport {new_pkg}.{class_name}")

    if content != original_content:
        with open(kt_file, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Updated imports in {kt_file.name}")

print("\nStep 3: Cleaning up empty directories...")
for folder in ["room", "repositories", "models", "calllog"]:
    folder_path = os.path.join(DATA_DIR, folder)
    if os.path.exists(folder_path):
        try:
            os.rmdir(folder_path)
            print(f"Removed empty directory {folder_path}")
        except OSError:
            print(f"Directory {folder_path} is not empty or cannot be removed.")

print("Refactoring complete!")
