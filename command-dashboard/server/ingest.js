import express from 'express';
import cors from 'cors';
import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const app = express();
app.use(cors());
app.use(express.json());

let reports = [];

const SEVERITY_WEIGHT = {
  "LOW": 1,
  "MEDIUM": 2,
  "HIGH": 3,
  "CRITICAL": 4
};

// --- Noise_N_25519_ChaChaPoly_SHA256 decryption (Tier 3) ---

const PROTOCOL_NAME = 'Noise_N_25519_ChaChaPoly_SHA256';

// Tier 3 command node's static X25519 public key (safe to embed — asymmetric).
const TIER3_STATIC_PUBLIC_KEY_HEX  = '7bfc76e82f21c7432de9155866a6947f49ba3cae8711ffc3af939c193b3d1644';

// Tier 3 private key: loaded from TIER3_PRIVATE_KEY env var, or from .tier3_key
// file in this directory. NEVER commit the private key to source control.
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const KEY_FILE = path.join(__dirname, '..', '.tier3_key');

function loadPrivateKey() {
  // 1. Try environment variable
  if (process.env.TIER3_PRIVATE_KEY) {
    return process.env.TIER3_PRIVATE_KEY.trim();
  }
  // 2. Try local key file
  if (fs.existsSync(KEY_FILE)) {
    return fs.readFileSync(KEY_FILE, 'utf-8').trim();
  }
  console.warn('WARNING: No Tier 3 private key found. Set TIER3_PRIVATE_KEY env var or create .tier3_key file.');
  console.warn('Binary payload decryption (POST /api/reports/raw) will fail.');
  return null;
}

const TIER3_STATIC_PRIVATE_KEY_HEX = loadPrivateKey();

// DER prefixes for converting raw X25519 keys to Node.js KeyObject format.
const PKCS8_PREFIX = Buffer.from('302e020100300506032b656e04220420', 'hex'); // PKCS8 for private
const SPKI_PREFIX  = Buffer.from('302a300506032b656e032100', 'hex');         // SPKI for public

function rawToPrivateKey(rawHex) {
  const der = Buffer.concat([PKCS8_PREFIX, Buffer.from(rawHex, 'hex')]);
  return crypto.createPrivateKey({ key: der, format: 'der', type: 'pkcs8' });
}

function rawToPublicKey(rawHex) {
  const der = Buffer.concat([SPKI_PREFIX, Buffer.from(rawHex, 'hex')]);
  return crypto.createPublicKey({ key: der, format: 'der', type: 'spki' });
}

function hmacSha256(key, data) {
  return crypto.createHmac('sha256', key).update(data).digest();
}

function sha256(data) {
  return crypto.createHash('sha256').update(data).digest();
}

function noiseHkdf(ck, ikm) {
  const tempKey = hmacSha256(ck, ikm);
  const output1 = hmacSha256(tempKey, Buffer.from([0x01]));
  const output2 = hmacSha256(tempKey, Buffer.concat([output1, Buffer.from([0x02])]));
  return { ck: output1, k: output2 };
}

/**
 * Decrypt a Noise_N encrypted blob.
 * Input: Buffer containing [32-byte ephemeral pubkey] [ciphertext + 16-byte Poly1305 tag]
 * Returns: decrypted plaintext Buffer, or throws on auth failure.
 */
function noiseNDecrypt(encryptedBlob) {
  if (!TIER3_STATIC_PRIVATE_KEY_HEX) {
    throw new Error('Tier 3 private key not loaded. Set TIER3_PRIVATE_KEY env var or create .tier3_key file.');
  }
  if (encryptedBlob.length < 48) { // 32 ephemeral + 16 tag minimum
    throw new Error('Encrypted blob too short for Noise_N');
  }

  // 1. Initialize symmetric state
  const protocolNameBytes = Buffer.from(PROTOCOL_NAME, 'ascii');
  let h = Buffer.alloc(32);
  protocolNameBytes.copy(h, 0, 0, protocolNameBytes.length); // pad to 32 with zeros
  let ck = Buffer.from(h);

  // 2. Pre-message: MixHash(rs) — our own static public key
  const rs = Buffer.from(TIER3_STATIC_PUBLIC_KEY_HEX, 'hex');
  h = sha256(Buffer.concat([h, rs]));

  // 3. Read ephemeral public key (first 32 bytes)
  const ephPubRaw = encryptedBlob.subarray(0, 32);
  const ciphertextWithTag = encryptedBlob.subarray(32);

  // MixHash(e.public)
  h = sha256(Buffer.concat([h, ephPubRaw]));

  // 4. es: DH(s, e) — our static private key × their ephemeral public key
  const staticPriv = rawToPrivateKey(TIER3_STATIC_PRIVATE_KEY_HEX);
  const ephPub = rawToPublicKey(ephPubRaw.toString('hex'));
  const dhResult = crypto.diffieHellman({ privateKey: staticPriv, publicKey: ephPub });

  // 5. MixKey(DH result)
  const hkdfResult = noiseHkdf(ck, dhResult);
  ck = hkdfResult.ck;
  const k = hkdfResult.k;

  // 6. DecryptAndHash: ChaCha20-Poly1305 with key=k, nonce=0, AD=h
  const nonce = Buffer.alloc(12); // n=0
  const tag = ciphertextWithTag.subarray(ciphertextWithTag.length - 16);
  const ciphertext = ciphertextWithTag.subarray(0, ciphertextWithTag.length - 16);

  const decipher = crypto.createDecipheriv('chacha20-poly1305', k, nonce, { authTagLength: 16 });
  decipher.setAAD(h);
  decipher.setAuthTag(tag);
  const plaintext = Buffer.concat([decipher.update(ciphertext), decipher.final()]);

  return plaintext;
}

// --- Wire format deserialization ---

const EMERGENCY_TYPES = ['TRAPPED', 'INJURED', 'FIRE', 'NEED_EVAC'];
const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const HEADER_SIZE = 43;

/**
 * Deserialize a full binary payload (43-byte plaintext header + encrypted notes).
 * Decrypts the notes portion using Noise_N, then returns a JSON report object.
 */
function deserializeBinaryPayload(payloadBuffer) {
  if (payloadBuffer.length < HEADER_SIZE) {
    throw new Error(`Payload too short: ${payloadBuffer.length} bytes, need at least ${HEADER_SIZE}`);
  }

  // Read plaintext header (little-endian)
  const version = payloadBuffer.readUInt8(0);
  const emergencyType = EMERGENCY_TYPES[payloadBuffer.readUInt8(1)] || 'TRAPPED';
  const severity = SEVERITIES[payloadBuffer.readUInt8(2)] || 'LOW';
  const casualtyCount = payloadBuffer.readUInt16LE(3);
  const timestamp = Number(payloadBuffer.readBigInt64LE(5));
  const hasLocation = payloadBuffer.readUInt8(13) !== 0;
  const latitude = payloadBuffer.readFloatLE(14);
  const longitude = payloadBuffer.readFloatLE(18);
  const corroborationCount = payloadBuffer.readUInt16LE(22);
  const ttl = payloadBuffer.readUInt8(24);

  // reportId: 16 bytes at offset 25, big-endian UUID
  const uuidBytes = payloadBuffer.subarray(25, 41);
  const hex = uuidBytes.toString('hex');
  const reportId = `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;

  const notesLength = payloadBuffer.readUInt16LE(41);

  // Decrypt notes
  let notes = '';
  if (notesLength > 0 && payloadBuffer.length > HEADER_SIZE) {
    const encryptedNotes = payloadBuffer.subarray(HEADER_SIZE, HEADER_SIZE + notesLength);
    const decryptedCompressed = noiseNDecrypt(encryptedNotes);

    // Decompress LZ4: first 4 bytes are original size (uint32 LE), rest is LZ4 data
    // For now, the gateway skips LZ4 decompression and reads raw UTF-8.
    // LZ4 decompression requires the lz4 npm package — adding it here.
    const originalSize = decryptedCompressed.readUInt32LE(0);
    const lz4Data = decryptedCompressed.subarray(4);

    // Simple LZ4 block decompression (fast decompressor)
    notes = lz4BlockDecode(lz4Data, originalSize).toString('utf-8');
  }

  return {
    reportId, emergencyType, severity, casualtyCount,
    notes, timestamp, hasLocation, latitude, longitude,
    corroborationCount, ttl
  };
}

/**
 * Minimal LZ4 block decoder (fast/raw format, no framing).
 * Matches the output of Java's LZ4Factory.fastCompressor().
 */
function lz4BlockDecode(input, originalSize) {
  const output = Buffer.alloc(originalSize);
  let ip = 0;
  let op = 0;

  while (ip < input.length) {
    const token = input[ip++];
    let literalLength = (token >> 4) & 0x0f;
    if (literalLength === 15) {
      let b;
      do { b = input[ip++]; literalLength += b; } while (b === 255);
    }

    // Copy literals
    input.copy(output, op, ip, ip + literalLength);
    ip += literalLength;
    op += literalLength;

    if (ip >= input.length) break; // End of block

    // Match offset (2 bytes, little-endian)
    const offset = input[ip] | (input[ip + 1] << 8);
    ip += 2;

    let matchLength = (token & 0x0f) + 4; // minMatch = 4
    if ((token & 0x0f) === 15) {
      let b;
      do { b = input[ip++]; matchLength += b; } while (b === 255);
    }

    // Copy match (may overlap, must copy byte-by-byte)
    let matchPos = op - offset;
    for (let i = 0; i < matchLength; i++) {
      output[op++] = output[matchPos++];
    }
  }

  return output.subarray(0, op);
}

// --- API ---

function calculateWeight(report) {
  const baseSeverity = SEVERITY_WEIGHT[report.severity] || 1;
  const corrobs = report.corroborationCount || 0;
  return baseSeverity * Math.log(1 + corrobs);
}

// Existing JSON endpoint — for seed data and pre-decrypted reports
app.post('/api/reports', (req, res) => {
  const data = req.body;
  if (!data.reportId) {
    return res.status(400).json({ error: 'reportId is required' });
  }

  // update or insert
  const existingIdx = reports.findIndex(r => r.reportId === data.reportId);
  if (existingIdx !== -1) {
    reports[existingIdx] = data;
  } else {
    reports.push(data);
  }

  res.json({ success: true });
});

// Binary payload endpoint — accepts base64-encoded raw payloads from the gateway,
// decrypts the Noise_N encrypted notes, deserializes, and stores.
app.post('/api/reports/raw', (req, res) => {
  try {
    const { payload } = req.body; // base64-encoded binary payload
    if (!payload) {
      return res.status(400).json({ error: 'payload (base64) is required' });
    }

    const payloadBuffer = Buffer.from(payload, 'base64');
    const report = deserializeBinaryPayload(payloadBuffer);

    // update or insert
    const existingIdx = reports.findIndex(r => r.reportId === report.reportId);
    if (existingIdx !== -1) {
      const existing = reports[existingIdx];
      // A corroboration-only update has no notes. Preserve existing notes and original identity fields.
      reports[existingIdx] = {
        ...existing,
        ...report,
        severity: existing.severity,
        casualtyCount: existing.casualtyCount,
        hasLocation: existing.hasLocation,
        latitude: existing.latitude,
        longitude: existing.longitude,
        notes: report.notes || existing.notes,
        corroborationCount: Math.max(existing.corroborationCount || 0, report.corroborationCount || 0)
      };
    } else {
      reports.push(report);
    }

    res.json({ success: true, report });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.get('/api/reports', (req, res) => {
  // Sort reports:
  // Primary key: baseSeverity * log(1 + corroborationCount) descending
  // Secondary key: TTL-remaining ascending
  const sortedReports = [...reports].sort((a, b) => {
    const weightA = calculateWeight(a);
    const weightB = calculateWeight(b);
    
    // Sort descending by weight
    if (Math.abs(weightA - weightB) > 0.0001) {
      return weightB - weightA;
    }
    
    // Sort ascending by TTL
    const ttlA = a.ttl || 0;
    const ttlB = b.ttl || 0;
    return ttlA - ttlB;
  });

  res.json(sortedReports);
});

const PORT = 3001;
app.listen(PORT, () => {
  console.log(`Ingest server running on port ${PORT}`);
});
