// Daily backup of all Supabase tables to the `backups` storage bucket.
// Runs on the schedule defined in netlify.toml.
//
// Required env vars (set in Netlify dashboard):
//   SUPABASE_URL                – e.g. https://wgqamqzlfnjcqyprphkw.supabase.co
//   SUPABASE_SERVICE_ROLE_KEY   – the service_role key from Supabase API settings
//
// Required bucket: a private bucket called `backups` (create in Supabase Storage).

const TABLES = [
  'jobs', 'employees', 'time_entries', 'inquiries',
  'shared_items', 'revisions', 'post_suggestions',
  'gear_checklists', 'assignment_requests',
  'availability_requests', 'push_subscriptions'
];

const RETENTION_DAYS = 30;

exports.handler = async () => {
  const url = process.env.SUPABASE_URL;
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!url || !key) {
    return { statusCode: 500, body: JSON.stringify({ error: 'Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY' }) };
  }

  const auth = { apikey: key, Authorization: `Bearer ${key}` };
  const dump = { backupAt: new Date().toISOString(), tables: {} };
  const errors = [];

  for (const t of TABLES) {
    try {
      const res = await fetch(`${url}/rest/v1/${t}?select=*`, { headers: auth });
      if (!res.ok) { errors.push(`${t}: ${res.status}`); dump.tables[t] = []; continue; }
      dump.tables[t] = await res.json();
    } catch (e) {
      errors.push(`${t}: ${e.message}`);
      dump.tables[t] = [];
    }
  }

  const today = new Date().toISOString().slice(0, 10);
  const filename = `backup-${today}.json`;
  const body = JSON.stringify(dump);

  const uploadRes = await fetch(`${url}/storage/v1/object/backups/${filename}`, {
    method: 'POST',
    headers: { ...auth, 'Content-Type': 'application/json', 'x-upsert': 'true' },
    body
  });
  if (!uploadRes.ok) {
    return { statusCode: 500, body: JSON.stringify({ error: 'Upload failed', detail: await uploadRes.text(), errors }) };
  }

  // Cleanup: delete backups older than retention
  try {
    const listRes = await fetch(`${url}/storage/v1/object/list/backups`, {
      method: 'POST',
      headers: { ...auth, 'Content-Type': 'application/json' },
      body: JSON.stringify({ limit: 1000, prefix: '' })
    });
    if (listRes.ok) {
      const files = await listRes.json();
      const cutoff = Date.now() - RETENTION_DAYS * 24 * 60 * 60 * 1000;
      const old = files.filter(f => f.created_at && new Date(f.created_at).getTime() < cutoff);
      for (const f of old) {
        await fetch(`${url}/storage/v1/object/backups/${f.name}`, { method: 'DELETE', headers: auth });
      }
    }
  } catch (e) {
    errors.push(`cleanup: ${e.message}`);
  }

  return {
    statusCode: 200,
    body: JSON.stringify({
      ok: true,
      filename,
      bytes: body.length,
      rowCounts: Object.fromEntries(Object.entries(dump.tables).map(([t, rows]) => [t, rows.length])),
      errors
    })
  };
};
