with open(r'c:\Pieter Folders\WinkerkReader\Winkerk10Reader 2024\app\src\main\kotlin\za\co\jpsoft\winkerkreader\ui\activities\MainActivity.kt', 'rb') as f:
    content = f.read()
    start = content.find(b'override fun onCreate')
    if start != -1:
        print(content[start:start+500].replace(b'\r', b'\\r').replace(b'\n', b'\\n').replace(b'\t', b'\\t'))
    else:
        print('Not found')
