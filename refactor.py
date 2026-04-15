import os
import re

replacements = {
    r"winkerkEntry\.SORTORDER": "AppSessionState.sortOrder",
    r"winkerkEntry\.SOEKLIST": "AppSessionState.soekList",
    r"winkerkEntry\.SOEK": "AppSessionState.soek",
    r"winkerkEntry\.DETAIL": "AppSessionState.detail",
    r"winkerkEntry\.LIDMAATID": "AppSessionState.lidmaatId",
    r"winkerkEntry\.LIDMAATGUID": "AppSessionState.lidmaatGuid",
    r"winkerkEntry\.GESINNGUID": "AppSessionState.gesinGuid",
    r"winkerkEntry\.LOADER": "AppSessionState.loader",
    r"winkerkEntry\.id": "AppSessionState.deviceId",
    r"winkerkEntry\.RECORDSTATUS": "AppSessionState.recordStatus",
    r"winkerkEntry\.GROEP_SMS_NAAM": "AppSessionState.groepSmsNaam",
    r"winkerkEntry\.KEUSE": "AppSessionState.keuse",
    r"winkerkEntry\.LISTVIEW": "AppSessionState.listView",
    r"winkerkEntry\.GROEPVIEW": "AppSessionState.groepView",
    r"winkerkEntry\.WINKERK_DB_DATUM": "AppSessionState.winkerkDbDatum"
}

settings_replacements = {
    r"winkerkEntry\.OPROEPMONITOR": "callMonitorEnabled",
    r"winkerkEntry\.DEFLAYOUT": "defLayout",
    r"winkerkEntry\.EPOSHTML": "eposHtml",
    r"winkerkEntry\.GEMEENTE_NAAM": "gemeenteNaam",
    r"winkerkEntry\.GEMEENTE_EPOS": "gemeenteEpos",
    r"winkerkEntry\.GEMEENTE_KLEUR": "gemeenteKleur",
    r"winkerkEntry\.GEMEENTE2_NAAM": "gemeente2Naam",
    r"winkerkEntry\.GEMEENTE2_EPOS": "gemeente2Epos",
    r"winkerkEntry\.GEMEENTE2_KLEUR": "gemeente2Kleur",
    r"winkerkEntry\.GEMEENTE3_NAAM": "gemeente3Naam",
    r"winkerkEntry\.GEMEENTE3_EPOS": "gemeente3Epos",
    r"winkerkEntry\.GEMEENTE3_KLEUR": "gemeente3Kleur",
    r"winkerkEntry\.DATA_DATUM": "dataDatum",
    r"winkerkEntry\.LIST_FOTO": "isListFoto",
    r"winkerkEntry\.LIST_VERJAARBLOK": "isListVerjaarBlok",
    r"winkerkEntry\.LIST_HUWELIKBLOK": "isListHuwelikBlok",
    r"winkerkEntry\.LIST_WYK": "isListWyk",
    r"winkerkEntry\.LIST_WHATSAPP": "isListWhatsapp",
    r"winkerkEntry\.LIST_EPOS": "isListEpos",
    r"winkerkEntry\.LIST_OUDERDOM": "isListOuderdom",
    r"winkerkEntry\.LIST_SELFOON": "isListSelfoon",
    r"winkerkEntry\.LIST_TELEFOON": "isListTelefoon",
    r"winkerkEntry\.WHATSAPP1": "whatsapp1",
    r"winkerkEntry\.WHATSAPP2": "whatsapp2",
    r"winkerkEntry\.WHATSAPP3": "whatsapp3",
    r"winkerkEntry\.WIDGET_DOOP": "widgetDoop",
    r"winkerkEntry\.WIDGET_BELYDENIS": "widgetBelydenis",
    r"winkerkEntry\.WIDGET_HUWELIK": "widgetHuwelik",
    r"winkerkEntry\.WIDGET_STERF": "widgetSterf"
}

directory = r"c:\Pieter Folders\WinkerkReader\Winkerk10Reader 2024\app\src\main\kotlin"

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original_content = content

    for pattern, replacement in replacements.items():
        content = re.sub(pattern, replacement, content)
        
    for pattern, replacement in settings_replacements.items():
        # Requires context. Will write 'SettingsManager(this).xxx' for Activities and 'SettingsManager(context).xxx' everywhere else if they have context scope, but for safety, 
        # actually let's use a global getter if we can, or just replace it with SettingsManager(context).xxx and fix compile errors. 
        # Wait, using `za.co.jpsoft.winkerkreader.utils.SettingsManager(context).` is safer but `context` might not be in scope.
        # Let's replace it with `za.co.jpsoft.winkerkreader.utils.SettingsManager(context).xxx` and we will fix context manually where needed.
        # Wait! It's safer to just inject it per file. Let's start with `SettingsManager(context).`
        content = re.sub(pattern, f"za.co.jpsoft.winkerkreader.utils.SettingsManager(context).{replacement}", content)

    # Some files use "this" instead of "context" inside Activities. We'll fix manually or use a trick:
    if "class " in content and "CompatActivity" in content:
         content = content.replace("SettingsManager(context)", "SettingsManager(this)")

    if content != original_content:
        # Add import for AppSessionState if not present
        if "AppSessionState" in content and "import za.co.jpsoft.winkerkreader.AppSessionState" not in content:
            content = content.replace("package za.co.jpsoft.winkerkreader.data\n", "package za.co.jpsoft.winkerkreader.data\n\nimport za.co.jpsoft.winkerkreader.AppSessionState\n")
            
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Updated {os.path.basename(filepath)}")

for root, dirs, files in os.walk(directory):
    for file in files:
        if file.endswith(".kt"):
            process_file(os.path.join(root, file))

print("Done")
