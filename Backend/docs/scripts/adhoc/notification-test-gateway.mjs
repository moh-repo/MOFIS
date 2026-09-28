#!/usr/bin/env node
/*
 * notification-test-gateway.mjs — a local stand-in for the SMTP relay, the SMS
 * gateway and the WhatsApp Cloud API, so the notification screens can be tested
 * end to end without a single message leaving this machine. DEV ONLY.
 *
 * Pairs with seed_sample_notifications.sql, which points every notification
 * settings row at it:
 *
 *   SMTP      127.0.0.1:2525   accepts any login; STARTTLS is not offered
 *   SMS       POST http://127.0.0.1:8089/sms
 *   WhatsApp  POST http://127.0.0.1:8089/whatsapp/...   (Cloud API shaped)
 *   Inbox     GET  http://127.0.0.1:8089/               (everything received, newest first)
 *
 * Simulated outcomes, so every status on the history page can be produced:
 *
 *   - An e-mail address containing "bounce" is refused (550) -> a failed recipient.
 *   - A phone number ending in 13 fails on SMS and WhatsApp (HTTP 500).
 *   - WhatsApp free text to a number whose last digit is ODD is refused with
 *     Cloud API error 131047 ("outside the 24-hour window"). A template message
 *     is always accepted. So: TEXT mode gives PARTIAL, TEMPLATE and AUTO give SENT.
 *   - SMS and WhatsApp need the header "Authorization: Bearer test-token" (401
 *     otherwise), which is what the seed stores in the write-only headers.
 *
 * The backend refuses loopback targets unless told otherwise (security plan
 * O-36), so start it with MOHE_OUTBOUND_ALLOW_PRIVATE=true while testing.
 *
 * Run:  node Backend/docs/scripts/adhoc/notification-test-gateway.mjs
 * Stop: Ctrl+C. Nothing is written to disk.
 */
import net from 'node:net';
import http from 'node:http';

const SMTP_PORT = Number(process.env.SMTP_PORT || 2525);
const HTTP_PORT = Number(process.env.HTTP_PORT || 8089);
const TOKEN = 'Bearer test-token';
const inbox = [];
let seq = 0;

function record(channel, to, summary, outcome) {
    const entry = { n: ++seq, at: new Date().toISOString(), channel, to, summary, outcome };
    inbox.unshift(entry);
    inbox.length = Math.min(inbox.length, 500);
    const mark = outcome === 'accepted' ? '+' : '-';
    console.log(`${mark} ${entry.at} ${channel.padEnd(8)} ${String(to).padEnd(28)} ${outcome.padEnd(22)} ${summary}`);
}

// ---------------------------------------------------------------- SMTP --------

/** "=?UTF-8?B?...?=" words in a header, decoded for the console. */
function decodeHeader(value) {
    return value.replace(/=\?utf-8\?b\?([^?]*)\?=/gi, (_, b64) => Buffer.from(b64, 'base64').toString('utf8'));
}

net.createServer(socket => {
    socket.setEncoding('utf8');
    let buffer = '';
    let inData = false;
    let authStep = null;
    let mail = { from: null, to: [], data: [] };
    const reply = line => socket.write(line + '\r\n');
    reply('220 mohe-test-smtp ready');

    const handle = line => {
        if (inData) {
            if (line === '.') {
                inData = false;
                const headers = mail.data.join('\n').split('\n\n')[0];
                const subject = decodeHeader((/^Subject: (.*(?:\n[ \t].*)*)/mi.exec(headers)?.[1] ?? '').replace(/\n[ \t]/g, ''));
                mail.to.forEach(to => record('EMAIL', to, subject || '(no subject)', 'accepted'));
                reply('250 2.0.0 OK queued');
                mail = { from: null, to: [], data: [] };
            } else {
                mail.data.push(line.startsWith('..') ? line.slice(1) : line);
            }
            return;
        }
        if (authStep === 'user') { authStep = 'pass'; reply('334 UGFzc3dvcmQ6'); return; }
        if (authStep === 'pass' || authStep === 'plain') { authStep = null; reply('235 2.7.0 Authentication successful'); return; }

        const [verb, ...rest] = line.split(' ');
        const arg = rest.join(' ');
        switch (verb.toUpperCase()) {
            case 'EHLO':
                socket.write('250-mohe-test-smtp\r\n250-AUTH LOGIN PLAIN\r\n250-8BITMIME\r\n250 SMTPUTF8\r\n');
                break;
            case 'HELO': reply('250 mohe-test-smtp'); break;
            case 'AUTH': {
                const [mech, initial] = arg.split(' ');
                if (mech?.toUpperCase() === 'PLAIN') {
                    if (initial) reply('235 2.7.0 Authentication successful');
                    else { authStep = 'plain'; reply('334 '); }
                } else {
                    if (initial) { authStep = 'pass'; reply('334 UGFzc3dvcmQ6'); }
                    else { authStep = 'user'; reply('334 VXNlcm5hbWU6'); }
                }
                break;
            }
            case 'MAIL': mail.from = arg; reply('250 2.1.0 OK'); break;
            case 'RCPT': {
                const to = (/<([^>]*)>/.exec(arg)?.[1]) ?? arg;
                if (/bounce/i.test(to)) {
                    record('EMAIL', to, '', 'refused (550 bounce)');
                    reply('550 5.1.1 Mailbox unavailable (simulated bounce)');
                } else {
                    mail.to.push(to);
                    reply('250 2.1.5 OK');
                }
                break;
            }
            case 'DATA':
                if (mail.to.length === 0) { reply('554 5.5.1 No valid recipients'); break; }
                inData = true;
                reply('354 End data with <CR><LF>.<CR><LF>');
                break;
            case 'RSET': mail = { from: null, to: [], data: [] }; reply('250 2.0.0 OK'); break;
            case 'NOOP': reply('250 2.0.0 OK'); break;
            case 'STARTTLS': reply('454 4.7.0 TLS not available on the test relay'); break;
            case 'QUIT': reply('221 2.0.0 Bye'); socket.end(); break;
            default: reply('502 5.5.2 Command not recognised');
        }
    };

    socket.on('data', chunk => {
        buffer += chunk;
        let i;
        while ((i = buffer.indexOf('\r\n')) >= 0) {
            const line = buffer.slice(0, i);
            buffer = buffer.slice(i + 2);
            handle(line);
        }
    });
    socket.on('error', () => { /* the client hung up */ });
}).listen(SMTP_PORT, '127.0.0.1', () => console.log(`SMTP relay      smtp://127.0.0.1:${SMTP_PORT}`));

// ---------------------------------------------------------------- HTTP --------

function json(res, status, body) {
    res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(body, null, 2));
}

http.createServer((req, res) => {
    if (req.method === 'GET') {
        return json(res, 200, { received: inbox.length, messages: inbox });
    }
    let raw = '';
    req.setEncoding('utf8');
    req.on('data', c => { raw += c; });
    req.on('end', () => {
        let body = {};
        try { body = JSON.parse(raw || '{}'); } catch { return json(res, 400, { error: 'body is not JSON' }); }
        const channel = req.url.startsWith('/sms') ? 'SMS' : req.url.startsWith('/whatsapp') ? 'WHATSAPP' : null;
        if (!channel) return json(res, 404, { error: 'unknown path ' + req.url });

        const to = String(body.to ?? '');
        if (req.headers.authorization !== TOKEN) {
            record(channel, to, '', 'refused (401 token)');
            return json(res, 401, { error: 'missing or wrong Authorization header' });
        }
        const digits = to.replace(/\D/g, '');
        if (digits.endsWith('13')) {
            record(channel, to, '', 'failed (500 simulated)');
            return json(res, 500, { error: 'simulated gateway failure for numbers ending in 13' });
        }
        if (channel === 'SMS') {
            record('SMS', to, String(body.body ?? '').slice(0, 60), 'accepted');
            return json(res, 200, { status: 'queued', id: 'sms-' + seq });
        }
        if (body.type === 'template') {
            const text = body.template?.components?.[0]?.parameters?.[0]?.text ?? '';
            record('WHATSAPP', to, `[template ${body.template?.name}] ${String(text).slice(0, 50)}`, 'accepted');
            return json(res, 200, { messaging_product: 'whatsapp', messages: [{ id: 'wamid.test-' + seq }] });
        }
        if (Number(digits.slice(-1)) % 2 === 1) {
            record('WHATSAPP', to, '[text]', 'refused (131047 window)');
            return json(res, 400, { error: {
                message: '(#131047) Re-engagement message',
                type: 'OAuthException', code: 131047,
                error_data: { details: 'Message failed to send because more than 24 hours have passed since the customer last replied to this number.' }
            } });
        }
        record('WHATSAPP', to, `[text] ${String(body.text?.body ?? '').slice(0, 50)}`, 'accepted');
        return json(res, 200, { messaging_product: 'whatsapp', messages: [{ id: 'wamid.test-' + seq }] });
    });
}).listen(HTTP_PORT, '127.0.0.1', () => {
    console.log(`SMS gateway     http://127.0.0.1:${HTTP_PORT}/sms`);
    console.log(`WhatsApp API    http://127.0.0.1:${HTTP_PORT}/whatsapp/...`);
    console.log(`Inbox           http://127.0.0.1:${HTTP_PORT}/`);
    console.log('Waiting for messages (Ctrl+C to stop)...');
});
