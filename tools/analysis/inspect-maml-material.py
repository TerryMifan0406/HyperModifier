"""Inspect local MAML/HyperOS Flutter material contracts without running device code.

Usage: python3 -B tools/analysis/inspect-maml-material.py \
  reference/desktop-miglass-20261021/maml.so \
  reference/desktop-miglass-20261021/libhyper_os_flutter.so \
  /local/widget_4x2/manifest.xml
Symbol presence is evidence of compiled capability, not of a live glyph-glass effect.
"""
import bisect
import hashlib
import json
import lzma
import struct
import sys
from collections import Counter
from pathlib import Path
import xml.etree.ElementTree as ET
from schema_elf import Elf

maml_path, engine_path, manifest_path = map(Path, sys.argv[1:4])
maml_bytes, engine_bytes = maml_path.read_bytes(), engine_path.read_bytes()

def symbols(data):
    image = Elf(data)
    if '.gnu_debugdata' in image.ss:
        return Elf(lzma.decompress(image.section('.gnu_debugdata')))
    return image

maml, engine = symbols(maml_bytes), symbols(engine_bytes)

def function(parts, table):
    matches = [(name, address, size) for name, (address, size) in table.syms.items()
               if all(part in name for part in parts)]
    if len(matches) != 1:
        raise SystemExit(f'Contract is missing/ambiguous: {parts}, count={len(matches)}')
    name, address, size = matches[0]
    return {'address': hex(address), 'bytes': size, 'symbol': name}

contracts = {
    'loadMaterial': function(['HyperMaterialHolder', '4load'], maml),
    'resolveGlassPassWindow': function(['GlassMaterialConfig', '19resolve_pass_window'], maml),
    'updateLayerMaterials': function(['LayerScreenElement', '26update_view_with_materials'], maml),
    'drawLayer': function(['LayerView', '7on_draw'], maml),
    'drawText': function(['TextScreenElement', '9draw_text'], maml),
    'convertMaterialProperties': function(['FFIMaterialProperties', '9from_data'], maml),
    'convertOutlineProperties': function(['FFIOutlineProperties', '9from_data'], maml),
    'createElement': function(['ElementGroup', '15on_create_child'], maml),
    'engineGlassV7Dispatcher': function(['FfiDispatcher', '11initGlassV7', '4Call'], engine),
    'engineGlassRender': function(['MiGlassContents', '6Render'], engine),
    'engineSaveLayer': function(['_ZN8impeller6Canvas9SaveLayerE'], engine),
    'engineDrawMaterial': function(['_ZN8impeller6Canvas17DrawHyperMaterialE'], engine),
    'engineFindGlass': function(['6Canvas15FindGlassFilter'], engine),
    'engineSdfTexture': function(['MiRenderHelper', '21GetOrCreateSdfTexture'], engine),
    'engineLightMask': function(['MiRenderHelper', '23TryCacheCreateLightMask'], engine),
    'engineBindGlassMask': function(['MiGlassV13F16FragmentShader', '12BindUMaskTex'], engine),
}

# Record direct BL call sites, not just matching names in unrelated code. Indirect
# calls/tail calls are deliberately not inferred from their absence in this map.
def direct_calls(data, table, keys):
    addresses = sorted(table.byaddr)
    targets = {int(contracts[key]['address'], 16): key for key in keys}
    calls = {key: [] for key in keys}
    for section in Elf(data).ss.values():
        if section[1] != 1 or not section[2] & 4:
            continue
        for offset in range(section[4], section[4] + section[5] - 3, 4):
            word = struct.unpack_from('<I', data, offset)[0]
            if word & 0xfc000000 != 0x94000000:
                continue
            pc = section[3] + offset - section[4]
            delta = word & 0x3ffffff
            if delta & (1 << 25): delta -= 1 << 26
            target = pc + (delta << 2)
            if target not in targets:
                continue
            index = bisect.bisect_right(addresses, pc) - 1
            owner = addresses[index] if index >= 0 else None
            name, size = table.byaddr[owner] if owner is not None else ('unknown', 0)
            if owner is not None and pc >= owner + size: name = 'unknown'
            calls[targets[target]].append({'call': hex(pc), 'owner': name})
    return calls

calls = direct_calls(maml_bytes, maml, ['loadMaterial', 'updateLayerMaterials',
                                      'convertMaterialProperties', 'convertOutlineProperties'])
engine_calls = direct_calls(engine_bytes, engine, ['engineDrawMaterial', 'engineFindGlass',
                           'engineSdfTexture', 'engineLightMask', 'engineBindGlassMask'])

# Record adjacent ADRP+ADD references inside the element factory. The referenced
# parser calls and lengths still need manual review; these are not an XML schema
# validator or a runtime ABI. Adjacency avoids speculative register tracking.
def outline_evidence():
    image = Elf(maml_bytes)
    start = int(contracts['createElement']['address'], 16)
    size = contracts['createElement']['bytes']
    words = struct.unpack('<' + 'I' * (size // 4), image.read(start, size))
    found = []
    for index in range(1, len(words)):
        previous, word = words[index - 1:index + 1]
        pc = start + index * 4
        if previous & 0x9f000000 != 0x90000000: continue  # ADRP
        if word & 0xffc00000 != 0x91000000: continue  # ADD, unshifted
        if previous & 31 != (word >> 5) & 31: continue
        delta = ((previous >> 29) & 3) | (((previous >> 5) & 0x7ffff) << 2)
        if delta & (1 << 20): delta -= 1 << 21
        address = ((pc - 4) & ~4095) + (delta << 12) + ((word >> 10) & 4095)
        for literal in [b'ViewOutlineProvider', b'pathData', b'scaleToFit']:
            try: matches = image.read(address, len(literal)) == literal
            except Exception: matches = False
            if matches:
                found.append({'literal': literal.decode(), 'reference': hex(pc),
                              'rodata': hex(address)})
    if {item['literal'] for item in found} != {'ViewOutlineProvider', 'pathData', 'scaleToFit'}:
        raise SystemExit('Outline attribute references are missing; inspect this library manually')
    return found

root = ET.parse(manifest_path).getroot()
counts = Counter(node.tag for node in root.iter())
report = {
    'mamlSha256': hashlib.sha256(maml_bytes).hexdigest(),
    'flutterSha256': hashlib.sha256(engine_bytes).hexdigest(),
    'contracts': contracts,
    'directCalls': calls,
    'engineDirectCalls': engine_calls,
    'outlineAttributeReferences': outline_evidence(),
    'resource': {
        'sha256': hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
        'frameRate': root.get('frameRate'), 'updater': root.get('useVariableUpdater'),
        'supportBackgroundBlur': root.get('supportBackgroundBlur'),
        'layerElements': counts['Layer'], 'materialTokens': counts['MaterialToken'],
        'glassConfigs': counts['GlassConfig'], 'dateTimeElements': counts['DateTime'],
        'classicTime': [node.attrib for node in root.iter('DateTime') if node.get('name') == 'center_classic_plus'],
    },
    'limits': ['No runtime Hook/FFI ABI verified', 'No glyph mask or backdrop sampling verified',
               'Path outline support does not prove glyph-edge refraction',
               'SDF and mask shader bindings do not establish their input geometry',
               'Direct calls omit tail calls and indirect dispatch',
               'Compiled material support does not prove that ordinary DateTime uses it'],
}
print(json.dumps(report, ensure_ascii=False, indent=2))
