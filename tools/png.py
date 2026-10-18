# Minimal PNG (8-bit RGBA, non-interlaced) reader / writer, to recolour icons without extra libraries.
import zlib, struct

def read(path):
    data = open(path, 'rb').read()
    pos, w, h, idat = 8, 0, 0, b''
    while pos < len(data):
        n = struct.unpack('>I', data[pos:pos + 4])[0]; kind = data[pos + 4:pos + 8]; body = data[pos + 8:pos + 8 + n]
        if kind == b'IHDR':
            w, h, depth, colour = struct.unpack('>IIBB', body[:10])
            assert depth == 8 and colour == 6, 'only 8-bit RGBA'
        elif kind == b'IDAT':
            idat += body
        pos += 12 + n
    raw = zlib.decompress(idat); stride = w * 4; rows = []; prev = bytearray(stride); i = 0
    for _ in range(h):
        f = raw[i]; line = bytearray(raw[i + 1:i + 1 + stride]); i += 1 + stride
        for x in range(stride):
            a = line[x - 4] if x >= 4 else 0; b = prev[x]; c = prev[x - 4] if x >= 4 else 0
            if f == 1: line[x] = (line[x] + a) & 255
            elif f == 2: line[x] = (line[x] + b) & 255
            elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line); prev = line
    return w, h, rows

def write(path, w, h, rows):
    raw = b''.join(b'\x00' + bytes(r) for r in rows)
    def chunk(k, b): return struct.pack('>I', len(b)) + k + b + struct.pack('>I', zlib.crc32(k + b) & 0xffffffff)
    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) +
                           chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))
