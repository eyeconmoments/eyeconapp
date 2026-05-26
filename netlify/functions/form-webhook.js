// Google Form → Supabase inquiries webhook
//
// SETUP (one-time):
// 1. Add FORM_WEBHOOK_TOKEN to Netlify env vars (any secret string you choose).
//    Optionally add SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY if not already set.
//
// 2. In your Google Form, open the three-dot menu → Script editor.
//    Paste the Apps Script below, set TOKEN + WEBHOOK_URL, then save and
//    add a trigger: "onFormSubmit" → On form submit.
//
// ── Google Apps Script ──────────────────────────────────────────────────────
// const TOKEN = 'YOUR_FORM_WEBHOOK_TOKEN';
// const WEBHOOK_URL = 'https://YOUR-SITE.netlify.app/.netlify/functions/form-webhook';
//
// function onFormSubmit(e) {
//   const r = e.response.getItemResponses();
//   const get = (label) => {
//     const match = r.find(i => i.getItem().getTitle().toLowerCase().includes(label.toLowerCase()));
//     return match ? match.getResponse() : '';
//   };
//   const payload = {
//     token: TOKEN,
//     name:      get('name'),
//     email:     get('email'),
//     phone:     get('phone'),
//     eventType: get('event type') || get('type of event') || get('coverage') || 'wedding',
//     eventDate: get('date') || get('event date') || get('wedding date'),
//     budget:    get('budget') || get('price'),
//     details:   get('detail') || get('message') || get('tell us') || get('additional'),
//   };
//   UrlFetchApp.fetch(WEBHOOK_URL, {
//     method: 'post',
//     contentType: 'application/json',
//     payload: JSON.stringify(payload),
//     muteHttpExceptions: true,
//   });
// }
// ────────────────────────────────────────────────────────────────────────────

const SUPABASE_AUTH = () => {
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
  return {
    apikey: key,
    Authorization: `Bearer ${key}`,
    'Content-Type': 'application/json',
    Prefer: 'return=representation',
  };
};

exports.handler = async (event) => {
  const cors = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type',
    'Content-Type': 'application/json',
  };

  if (event.httpMethod === 'OPTIONS') return { statusCode: 204, headers: cors };
  if (event.httpMethod !== 'POST') return { statusCode: 405, headers: cors, body: '{"error":"Method not allowed"}' };

  let body;
  try { body = JSON.parse(event.body || '{}'); } catch (_) { return { statusCode: 400, headers: cors, body: '{"error":"Invalid JSON"}' }; }

  // Token check — skip if FORM_WEBHOOK_TOKEN not set (dev mode)
  const expected = process.env.FORM_WEBHOOK_TOKEN;
  if (expected && body.token !== expected) {
    return { statusCode: 401, headers: cors, body: '{"error":"Unauthorized"}' };
  }

  if (!process.env.SUPABASE_URL || !process.env.SUPABASE_SERVICE_ROLE_KEY) {
    return { statusCode: 500, headers: cors, body: '{"error":"Supabase env vars missing"}' };
  }

  // Normalise event date — accept "15/06/2025", "2025-06-15", "June 15 2025", etc.
  let eventDate = null;
  if (body.eventDate) {
    const raw = String(body.eventDate).trim();
    // DD/MM/YYYY
    const dmyMatch = raw.match(/^(\d{1,2})[\/\-\.](\d{1,2})[\/\-\.](\d{4})$/);
    if (dmyMatch) {
      eventDate = `${dmyMatch[3]}-${dmyMatch[2].padStart(2, '0')}-${dmyMatch[1].padStart(2, '0')}`;
    } else {
      const parsed = new Date(raw);
      if (!isNaN(parsed)) eventDate = parsed.toISOString().slice(0, 10);
    }
  }

  const row = {
    customer_name: (body.name || '').trim() || 'Unknown',
    email:         (body.email || '').trim().toLowerCase() || null,
    phone:         (body.phone || '').trim() || null,
    event_type:    (body.eventType || 'wedding').toLowerCase().trim(),
    event_date:    eventDate,
    budget:        (body.budget || '').trim() || null,
    details:       (body.details || '').trim() || null,
    status:        'new',
    submitted_date: new Date().toISOString(),
  };

  try {
    const res = await fetch(`${process.env.SUPABASE_URL}/rest/v1/inquiries`, {
      method: 'POST',
      headers: SUPABASE_AUTH(),
      body: JSON.stringify(row),
    });
    const data = await res.json();
    if (!res.ok) throw new Error(JSON.stringify(data));
    console.log('Inquiry inserted:', row.customer_name, row.email);
    return { statusCode: 200, headers: cors, body: JSON.stringify({ ok: true, id: data?.[0]?.id }) };
  } catch (e) {
    console.error('form-webhook error:', e.message);
    return { statusCode: 500, headers: cors, body: JSON.stringify({ error: e.message }) };
  }
};
