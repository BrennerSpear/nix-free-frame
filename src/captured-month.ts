/** Read only EXIF DateTimeOriginal. Never substitute file, upload, or revision times. */
export function capturedMonthFromJpeg(bytes: Uint8Array): string | undefined {
  if (bytes.length < 4 || bytes[0] !== 0xff || bytes[1] !== 0xd8) return;
  for (let offset = 2; offset + 4 <= bytes.length;) {
    if (bytes[offset++] !== 0xff) return;
    while (bytes[offset] === 0xff) offset++;
    const marker = bytes[offset++];
    if (marker === 0xda || marker === 0xd9) return;
    if (marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) continue;
    if (offset + 2 > bytes.length) return;
    const length = (bytes[offset] << 8) | bytes[offset + 1];
    if (length < 2 || offset + length > bytes.length) return;
    const data = bytes.subarray(offset + 2, offset + length);
    if (marker === 0xe1 && data.length >= 14 && new TextDecoder().decode(data.subarray(0, 6)) === 'Exif\0\0') {
      const month = fromTiff(data.subarray(6));
      if (month) return month;
    }
    offset += length;
  }
}
function fromTiff(bytes: Uint8Array): string | undefined {
  const little = bytes[0] === 0x49 && bytes[1] === 0x49;
  if (!little && !(bytes[0] === 0x4d && bytes[1] === 0x4d)) return;
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const u16 = (offset: number) => view.getUint16(offset, little);
  const u32 = (offset: number) => view.getUint32(offset, little);
  const find = (offset: number, tag: number): number | undefined => {
    if (offset < 8 || offset + 2 > bytes.length) return;
    const count = u16(offset);
    if (offset + 2 + count * 12 + 4 > bytes.length) return;
    for (let i = 0; i < count; i++) {
      const entry = offset + 2 + i * 12;
      if (u16(entry) === tag) return entry;
    }
  };
  try {
    if (u16(2) !== 42) return;
    const pointer = find(u32(4), 0x8769);
    if (pointer === undefined || u16(pointer + 2) !== 4 || u32(pointer + 4) !== 1) return;
    const date = find(u32(pointer + 8), 0x9003);
    if (date === undefined || u16(date + 2) !== 2) return;
    const count = u32(date + 4), offset = u32(date + 8);
    if (count < 20 || count > 64 || offset < 8 || offset + count > bytes.length) return;
    const text = new TextDecoder().decode(bytes.subarray(offset, offset + count)).split('\0')[0];
    const match = /^(\d{4}):(\d{2}):(\d{2}) (\d{2}):(\d{2}):(\d{2})$/.exec(text);
    if (!match) return;
    const [year, month, day, hour, minute, second] = match.slice(1).map(Number);
    if (year < 1900 || month < 1 || month > 12 || day < 1 || hour > 23 || minute > 59 || second > 59) return;
    const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
    if (day > daysInMonth) return;
    return `${match[1]}-${match[2]}`;
  } catch { return; } // Malformed optional metadata must not discard an otherwise valid photo.
}
