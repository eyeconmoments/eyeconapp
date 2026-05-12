// Send an SMS via Twilio.
// Called from the app with POST { to, message }.
//
// Required env vars (set in Netlify dashboard):
//   TWILIO_ACCOUNT_SID   – from console.twilio.com
//   TWILIO_AUTH_TOKEN    – from console.twilio.com
//   TWILIO_FROM_NUMBER   – your Twilio phone number e.g. +447700900000

exports.handler = async (event) => {
  const headers = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type',
    'Content-Type': 'application/json',
  };
  if (event.httpMethod === 'OPTIONS') return { statusCode: 204, headers };
  if (event.httpMethod !== 'POST') return { statusCode: 405, headers, body: '{"error":"Method not allowed"}' };

  const sid = process.env.TWILIO_ACCOUNT_SID;
  const token = process.env.TWILIO_AUTH_TOKEN;
  const from = process.env.TWILIO_FROM_NUMBER;
  if (!sid || !token || !from) return { statusCode: 500, headers, body: JSON.stringify({ error: 'Twilio env vars not set' }) };

  const { to, message } = JSON.parse(event.body || '{}');
  if (!to || !message) return { statusCode: 400, headers, body: JSON.stringify({ error: 'to and message required' }) };

  const res = await fetch(`https://api.twilio.com/2010-04-01/Accounts/${sid}/Messages.json`, {
    method: 'POST',
    headers: {
      Authorization: 'Basic ' + Buffer.from(`${sid}:${token}`).toString('base64'),
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams({ From: from, To: to, Body: message }).toString(),
  });

  const data = await res.json();
  if (!res.ok) return { statusCode: res.status, headers, body: JSON.stringify({ error: data.message || 'Twilio error' }) };
  return { statusCode: 200, headers, body: JSON.stringify({ ok: true, sid: data.sid }) };
};
