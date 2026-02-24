import { rm } from 'fs/promises';
import P from 'pino';
import makeWASocket, {
  useMultiFileAuthState,
  DisconnectReason,
  fetchLatestBaileysVersion,
} from '@whiskeysockets/baileys';
import qrcode from 'qrcode-terminal';
import express from 'express';
import axios from 'axios';

const app = express();
const PORT = 3000;
const AUTH_FOLDER = 'auth_info';
const BACKEND_WEBHOOK_URL = 'http://localhost:8080/webhook/whatsapp';

const logger = P({ level: 'silent' });

app.use(express.json());

let activeSock = null;
let reconnectAttempt = 0;


app.get('/health', (req, res) => {
  res.json({ status: 'ok', service: 'whatsapp-bridge', connected: !!activeSock });
});

app.post('/send', async (req, res) => {
  const { to, text } = req.body || {};
  if (!to || typeof text !== 'string') {
    return res.status(400).json({ error: 'Missing or invalid "to" or "text"' });
  }
  if (!activeSock) {
    return res.status(503).json({ error: 'WhatsApp not connected' });
  }
  try {
    await activeSock.sendMessage(to, { text });
    return res.json({ ok: true });
  } catch (err) {
    console.error('Send failed:', err.message);
    return res.status(500).json({ error: err.message || 'Send failed' });
  }
});

app.listen(PORT, () => {
  console.log(`WhatsApp bridge running on http://localhost:${PORT}`);
});

// --- lid → phone resolution (Baileys v7 signalRepository.lidMapping) ---

async function resolvePhoneNumber(sock, remoteJid) {
  if (remoteJid.endsWith('@s.whatsapp.net')) {
    return remoteJid.replace(/@.*$/, '');
  }

  if (!remoteJid.endsWith('@lid')) {
    return null;
  }

  try {
    const phoneJid = await sock.signalRepository.lidMapping.getPNForLID(remoteJid);
    if (phoneJid) {
      const phone = phoneJid.replace(/@.*$/, '').replace(/:.*$/, '');
      console.log(`Resolved lid → phone ${phone}`);
      return phone;
    }
  } catch (err) {
    console.error('lid→phone resolution failed:', err.message);
  }

  return null;
}

// --- Baileys WhatsApp client ---

function isPersonalChat(jid) {
  return jid?.endsWith('@s.whatsapp.net') || jid?.endsWith('@lid');
}

async function connectWhatsApp() {
  const { state, saveCreds } = await useMultiFileAuthState(AUTH_FOLDER);

  const { version, isLatest } = await fetchLatestBaileysVersion();
  console.log(`Connecting to WhatsApp… (WA version: ${version.join('.')}, latest: ${isLatest})`);

  const sock = makeWASocket({
    auth: state,
    version,
    logger,
    browser: ['Clario', 'Chrome', '22.04.4'],
  });

  sock.ev.on('creds.update', saveCreds);

  sock.ev.on('messages.upsert', async ({ messages }) => {
    const msg = messages[0];
    if (!msg || msg.key.fromMe) return;

    const remoteJid = msg.key.remoteJid || '';
    if (!isPersonalChat(remoteJid)) return;

    const text =
      msg.message?.conversation ||
      msg.message?.extendedTextMessage?.text ||
      '';
    if (!text) return;

    const phone = await resolvePhoneNumber(sock, remoteJid);
    console.log(`Message from ${phone || remoteJid} — "${text.substring(0, 80)}"`);

    if (!phone) {
      console.warn(`Could not resolve phone number for ${remoteJid}, forwarding raw id`);
    }

    const payload = {
      object: 'whatsapp_business_account',
      entry: [
        {
          changes: [
            {
              value: {
                messages: [
                  {
                    from: phone || remoteJid.replace(/@.*$/, ''),
                    id: msg.key.id,
                    timestamp: String(msg.messageTimestamp || ''),
                    type: 'text',
                    text: { body: text },
                  },
                ],
              },
            },
          ],
        },
      ],
    };

    let reply;
    try {
      const res = await axios.post(BACKEND_WEBHOOK_URL, payload, {
        headers: { 'Content-Type': 'application/json' },
      });
      reply = res.data?.data?.responseMessage;
    } catch (err) {
      reply =
        err.response?.data?.data?.responseMessage ||
        err.response?.data?.error ||
        'Sorry, something went wrong while processing your message.';
      console.error('Webhook forward failed:', err.message);
    }

    if (reply) {
      try {
        await sock.sendMessage(remoteJid, { text: reply });
      } catch (sendErr) {
        console.error('Reply send failed:', sendErr.message);
      }
    }
  });

  sock.ev.on('connection.update', async (update) => {
    const { connection, lastDisconnect, qr } = update;

    if (qr) {
      console.log('\n========================================');
      console.log(' Scan this QR with WhatsApp > Linked Devices');
      console.log('========================================\n');
      qrcode.generate(qr, { small: true });
    }

    if (connection === 'open') {
      reconnectAttempt = 0;
      console.log('WhatsApp connected successfully!');
    }

    if (connection === 'close') {
      const statusCode = lastDisconnect?.error?.output?.statusCode;
      const reason = lastDisconnect?.error?.output?.payload?.message || 'unknown';
      console.log(`Connection closed — status: ${statusCode}, reason: ${reason}`);

      if (statusCode === DisconnectReason.loggedOut) {
        console.log('Session revoked. Removing auth and restarting…');
        await rm(AUTH_FOLDER, { recursive: true, force: true });
        reconnectAttempt = 0;
        scheduleReconnect();
        return;
      }

      scheduleReconnect();
    }
  });

  activeSock = sock;
  return sock;
}

function scheduleReconnect() {
  const baseDelay = 5_000;
  const maxDelay = 60_000;
  const delay = Math.min(baseDelay * Math.pow(2, reconnectAttempt), maxDelay);
  reconnectAttempt++;
  console.log(`Reconnecting in ${(delay / 1000).toFixed(0)}s… (attempt ${reconnectAttempt})`);
  setTimeout(() => {
    connectWhatsApp().catch((err) => {
      console.error('Reconnect failed:', err.message);
      scheduleReconnect();
    });
  }, delay);
}

connectWhatsApp()
  .then(() => {
    console.log('WhatsApp client initialized. Waiting for QR or session…');
  })
  .catch((err) => {
    console.error('WhatsApp connection error:', err.message);
    scheduleReconnect();
  });
