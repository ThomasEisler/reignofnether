# Minimal NBT reader / writer for structure files (gzip), used to make placeholder building models.
import gzip, struct, io

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)

class Tag:
    def __init__(self, type, value, list_type=None):
        self.type, self.value, self.list_type = type, value, list_type

def _read_payload(f, t):
    if t == BYTE: return struct.unpack('>b', f.read(1))[0]
    if t == SHORT: return struct.unpack('>h', f.read(2))[0]
    if t == INT: return struct.unpack('>i', f.read(4))[0]
    if t == LONG: return struct.unpack('>q', f.read(8))[0]
    if t == FLOAT: return struct.unpack('>f', f.read(4))[0]
    if t == DOUBLE: return struct.unpack('>d', f.read(8))[0]
    if t == BYTE_ARRAY:
        n = struct.unpack('>i', f.read(4))[0]; return list(f.read(n))
    if t == STRING:
        n = struct.unpack('>H', f.read(2))[0]; return f.read(n).decode('utf-8')
    if t == LIST:
        lt = f.read(1)[0]; n = struct.unpack('>i', f.read(4))[0]
        return Tag(LIST, [Tag(lt, _read_payload(f, lt)) for _ in range(n)], lt)
    if t == COMPOUND:
        d = {}
        while True:
            tt = f.read(1)[0]
            if tt == END: return d
            n = struct.unpack('>H', f.read(2))[0]; name = f.read(n).decode('utf-8')
            d[name] = Tag(tt, _read_payload(f, tt))
    if t == INT_ARRAY:
        n = struct.unpack('>i', f.read(4))[0]; return list(struct.unpack('>%di' % n, f.read(4 * n)))
    if t == LONG_ARRAY:
        n = struct.unpack('>i', f.read(4))[0]; return list(struct.unpack('>%dq' % n, f.read(8 * n)))
    raise ValueError(t)

def _write_payload(f, t, v):
    if t == BYTE: f.write(struct.pack('>b', v))
    elif t == SHORT: f.write(struct.pack('>h', v))
    elif t == INT: f.write(struct.pack('>i', v))
    elif t == LONG: f.write(struct.pack('>q', v))
    elif t == FLOAT: f.write(struct.pack('>f', v))
    elif t == DOUBLE: f.write(struct.pack('>d', v))
    elif t == BYTE_ARRAY: f.write(struct.pack('>i', len(v))); f.write(bytes(v))
    elif t == STRING:
        b = v.encode('utf-8'); f.write(struct.pack('>H', len(b))); f.write(b)
    elif t == LIST:
        items = v.value; lt = v.list_type if v.list_type is not None else (items[0].type if items else END)
        f.write(bytes([lt])); f.write(struct.pack('>i', len(items)))
        for it in items: _write_payload(f, lt, it.value)
    elif t == COMPOUND:
        for name, tag in v.items():
            f.write(bytes([tag.type])); b = name.encode('utf-8'); f.write(struct.pack('>H', len(b))); f.write(b)
            _write_payload(f, tag.type, tag.value)
        f.write(bytes([END]))
    elif t == INT_ARRAY: f.write(struct.pack('>i', len(v))); f.write(struct.pack('>%di' % len(v), *v))
    elif t == LONG_ARRAY: f.write(struct.pack('>i', len(v))); f.write(struct.pack('>%dq' % len(v), *v))

def read(path):
    with gzip.open(path, 'rb') as g:
        f = io.BytesIO(g.read())
    t = f.read(1)[0]; n = struct.unpack('>H', f.read(2))[0]; f.read(n)
    return _read_payload(f, t)

def write(path, root):
    f = io.BytesIO(); f.write(bytes([COMPOUND])); f.write(struct.pack('>H', 0)); _write_payload(f, COMPOUND, root)
    with gzip.open(path, 'wb') as g:
        g.write(f.getvalue())

# a structure from {(x, y, z): "minecraft:block[prop=val,...]"}
def structure(blocks, data_version):
    palette, index, entries = [], {}, []
    for pos, state in sorted(blocks.items()):
        if state not in index:
            index[state] = len(palette)
            name, props = (state.split('[', 1) + [''])[:2]
            p = {'Name': Tag(STRING, name)}
            if props:
                p['Properties'] = Tag(COMPOUND, {k: Tag(STRING, v) for k, v in (kv.split('=') for kv in props.rstrip(']').split(','))})
            palette.append(Tag(COMPOUND, p))
        entries.append(Tag(COMPOUND, {'pos': Tag(LIST, Tag(LIST, [Tag(INT, c) for c in pos], INT)), 'state': Tag(INT, index[state])}))
    xs, ys, zs = zip(*blocks)
    size = [max(xs) + 1, max(ys) + 1, max(zs) + 1]
    return {
        'DataVersion': Tag(INT, data_version),
        'size': Tag(LIST, Tag(LIST, [Tag(INT, c) for c in size], INT)),
        'palette': Tag(LIST, Tag(LIST, palette, COMPOUND)),
        'blocks': Tag(LIST, Tag(LIST, entries, COMPOUND)),
        'entities': Tag(LIST, Tag(LIST, [], COMPOUND)),
    }
