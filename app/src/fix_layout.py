#!/usr/bin/env python3
"""
Android XML Fixer Script
Automatically adds missing contentDescription and autofillHints to layout files
"""

import os
import re
import xml.etree.ElementTree as ET
from pathlib import Path

class AndroidLayoutFixer:
    def __init__(self, root_dir):
        self.root_dir = Path(root_dir)
        self.namespace = {'android': 'http://schemas.android.com/apk/res/android'}
        ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
        
    def fix_layout_file(self, file_path):
        """Process a single layout XML file"""
        try:
            # Parse XML
            tree = ET.parse(file_path)
            root = tree.getroot()
            
            modified = False
            
            # Fix ImageView elements
            for img in root.findall('.//ImageView'):
                if self.add_content_description(img):
                    modified = True
            
            # Fix EditText elements (and their subclasses)
            for edit in root.findall('.//EditText'):
                if self.add_autofill_hints(edit):
                    modified = True
                    
            # Also check for AppCompatEditText
            for edit in root.findall('.//androidx.appcompat.widget.AppCompatEditText'):
                if self.add_autofill_hints(edit):
                    modified = True
            
            # Save if modified
            if modified:
                # Pretty print XML
                self.prettify_and_save(tree, file_path)
                print(f"✓ Fixed: {file_path}")
                return True
            else:
                print(f"  No changes needed: {file_path}")
                return False
                
        except ET.ParseError as e:
            print(f"✗ Error parsing {file_path}: {e}")
            return False
        except Exception as e:
            print(f"✗ Error processing {file_path}: {e}")
            return False
    
    def add_content_description(self, element):
        """Add contentDescription to ImageView if missing"""
        # Check if contentDescription already exists
        if element.get('{http://schemas.android.com/apk/res/android}contentDescription') is not None:
            return False
        
        # Get the image source
        src = element.get('{http://schemas.android.com/apk/res/android}src')
        if not src:
            # Try background as fallback
            src = element.get('{http://schemas.android.com/apk/res/android}background')
            if not src:
                # Skip if no src or background
                return False
        
        # Extract the resource name
        resource_name = self.extract_resource_name(src)
        if not resource_name:
            return False
        
        # Clean the name
        clean_name = self.clean_image_name(resource_name)
        
        # Add contentDescription
        element.set('{http://schemas.android.com/apk/res/android}contentDescription', 
                   f'@string/{clean_name}')
        return True
    
    def add_autofill_hints(self, element):
        """Add autofillHints to EditText if missing"""
        # Check if autofillHints already exists
        if element.get('{http://schemas.android.com/apk/res/android}autofillHints') is not None:
            return False
        
        # Get the ID
        element_id = element.get('{http://schemas.android.com/apk/res/android}id')
        if not element_id:
            return False
        
        # Extract the ID name
        id_name = self.extract_id_name(element_id)
        if not id_name:
            return False
        
        # Clean the name
        clean_name = self.clean_edittext_name(id_name)
        
        # Add autofillHints
        element.set('{http://schemas.android.com/apk/res/android}autofillHints', 
                   f'Edit {clean_name}')
        return True
    
    def extract_resource_name(self, src):
        """Extract resource name from android:src or android:background"""
        # Handle @drawable/name format
        match = re.search(r'@drawable/([^)]+)', src)
        if match:
            return match.group(1)
        
        # Handle @mipmap/name format
        match = re.search(r'@mipmap/([^)]+)', src)
        if match:
            return match.group(1)
        
        # Handle ?attr/ format
        match = re.search(r'\?attr/([^)]+)', src)
        if match:
            return match.group(1)
        
        return None
    
    def extract_id_name(self, element_id):
        """Extract ID name from android:id"""
        # Handle @+id/name format
        match = re.search(r'@\+id/([^)]+)', element_id)
        if match:
            return match.group(1)
        
        # Handle @id/name format
        match = re.search(r'@id/([^)]+)', element_id)
        if match:
            return match.group(1)
        
        return None
    
    def clean_image_name(self, name):
        """Remove common prefixes from image names"""
        # Remove common prefixes
        prefixes_to_remove = [
            'ic_', 'ic_', 'ic_',
            'bg_', 'background_',
            'img_', 'image_',
            'icon_', 'icon_',
            'btn_', 'button_'
        ]
        
        clean_name = name
        for prefix in prefixes_to_remove:
            if clean_name.startswith(prefix):
                clean_name = clean_name[len(prefix):]
                break
        
        # Replace underscores with spaces and capitalize
        clean_name = clean_name.replace('_', ' ')
        clean_name = clean_name.title()
        
        return clean_name
    
    def clean_edittext_name(self, name):
        """Remove common prefixes from EditText IDs"""
        # Remove common prefixes
        prefixes_to_remove = [
            'detail_',
            'edit_',
            'input_',
            'txt_',
            'text_',
            'field_',
            'et_'
        ]
        
        clean_name = name
        for prefix in prefixes_to_remove:
            if clean_name.startswith(prefix):
                clean_name = clean_name[len(prefix):]
                break
        
        # Replace underscores with spaces and capitalize
        clean_name = clean_name.replace('_', ' ')
        clean_name = clean_name.title()
        
        return clean_name
    
    def prettify_and_save(self, tree, file_path):
        """Prettify and save the XML file"""
        # Convert to string
        xml_str = ET.tostring(tree.getroot(), encoding='unicode')
        
        # Parse with minidom for pretty printing
        import xml.dom.minidom
        dom = xml.dom.minidom.parseString(xml_str)
        pretty_xml = dom.toprettyxml(indent='    ')
        
        # Remove extra newlines at start
        pretty_xml = pretty_xml.split('\n', 1)[1] if '\n' in pretty_xml else pretty_xml
        
        # Write back
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(pretty_xml)
    
    def process_all_layouts(self):
        """Process all layout XML files in the directory"""
        layout_dir = self.root_dir / 'app' / 'src' / 'main' / 'res' / 'layout'
        
        if not layout_dir.exists():
            print(f"Layout directory not found: {layout_dir}")
            return
        
        modified_count = 0
        xml_files = list(layout_dir.glob('*.xml'))
        
        print(f"Found {len(xml_files)} layout files to process\n")
        
        for xml_file in xml_files:
            if self.fix_layout_file(xml_file):
                modified_count += 1
        
        print(f"\n✓ Fixed {modified_count} files")
        print(f"✓ Processed {len(xml_files)} files total")

def main():
    # Get the project root directory
    project_root = r'C:\Pieter Folders\WinkerkReader\Winkerk10Reader 2024'
    
    if not os.path.exists(project_root):
        print(f"Project directory not found: {project_root}")
        print("Please update the project_root variable with the correct path")
        return
    
    fixer = AndroidLayoutFixer(project_root)
    fixer.process_all_layouts()

if __name__ == "__main__":
    main()