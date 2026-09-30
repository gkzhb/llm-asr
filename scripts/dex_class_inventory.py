"""Read DEX class definitions, not reference strings (no Android runtime claim)."""
import struct


def defined_classes(data):
    if len(data) < 112 or not data.startswith(b'dex\n') or data[7] != 0:
        raise ValueError('invalid DEX header')
    if struct.unpack_from('<I', data, 40)[0] != 0x12345678:
        raise ValueError('unsupported DEX endianness')

    def table(header_offset, item_size):
        count, offset = struct.unpack_from('<II', data, header_offset)
        if offset > len(data) or count > (len(data) - offset) // item_size:
            raise ValueError('DEX table out of bounds')
        return count, offset

    strings_count, strings_offset = table(56, 4)
    types_count, types_offset = table(64, 4)
    classes_count, classes_offset = table(96, 32)
    classes = set()
    for i in range(classes_count):
        type_idx = struct.unpack_from('<I', data, classes_offset + i * 32)[0]
        if type_idx >= types_count:
            raise ValueError('DEX class type out of bounds')
        string_idx = struct.unpack_from('<I', data, types_offset + type_idx * 4)[0]
        if string_idx >= strings_count:
            raise ValueError('DEX descriptor index out of bounds')
        offset = struct.unpack_from('<I', data, strings_offset + string_idx * 4)[0]
        # Skip the bounded ULEB128 UTF-16 length; descriptors below are ASCII.
        for _ in range(5):
            if offset >= len(data):
                raise ValueError('DEX string out of bounds')
            byte = data[offset]
            offset += 1
            if byte < 128:
                break
        else:
            raise ValueError('invalid DEX string length')
        end = data.find(b'\0', offset)
        if end < 0:
            raise ValueError('unterminated DEX descriptor')
        descriptor = data[offset:end]
        if not descriptor.startswith(b'L') or not descriptor.endswith(b';'):
            raise ValueError('invalid DEX class descriptor')
        if descriptor in classes:
            raise ValueError('duplicate DEX class definition')
        classes.add(descriptor)
    return classes
