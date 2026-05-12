// Daily SMS reminders — runs every morning at 8am UTC.
// Sends shoot-day reminders to assigned staff for jobs shooting tomorrow.
// Sends deadline warnings to admin for jobs due in 3 days or fewer.
//
// Required env vars:
//   SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY
//   TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_FROM_NUMBER
//   ADMIN_PHONE  – mobile number to receive deadline alerts e.g. +447700900000

const TABLES_AUTH = () => {
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
  return { apikey: key, Authorization: `Bearer ${key}` };
};

async function fetchTable(table) {
  const url = process.env.SUPABASE_URL;
  const res = await fetch(`${url}/rest/v1/${table}?select=*`, { headers: TABLES_AUTH() });
  if (!res.ok) return [];
  return res.json();
}

async function sendSMS(to, message) {
  const sid = process.env.TWILIO_ACCOUNT_SID;
  const token = process.env.TWILIO_AUTH_TOKEN;
  const from = process.env.TWILIO_FROM_NUMBER;
  if (!sid || !token || !from || !to) return { ok: false, error: 'missing config' };
  const res = await fetch(`https://api.twilio.com/2010-04-01/Accounts/${sid}/Messages.json`, {
    method: 'POST',
    headers: {
      Authorization: 'Basic ' + Buffer.from(`${sid}:${token}`).toString('base64'),
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams({ From: from, To: to, Body: message }).toString(),
  });
  const data = await res.json();
  return res.ok ? { ok: true, sid: data.sid } : { ok: false, error: data.message };
}

exports.handler = async () => {
  const [jobs, employees] = await Promise.all([fetchTable('jobs'), fetchTable('employees')]);
  const empById = Object.fromEntries(employees.map(e => [e.id, e]));
  const results = [];

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const tomorrow = new Date(today); tomorrow.setDate(today.getDate() + 1);
  const in3Days = new Date(today); in3Days.setDate(today.getDate() + 3);

  for (const job of jobs) {
    if (job.archived) continue;
    const shootDate = job.shoot_date ? new Date(job.shoot_date) : null;
    const deadline = job.deadline ? new Date(job.deadline) : null;

    // Shoot-day reminder to assigned staff
    if (shootDate) {
      shootDate.setHours(0, 0, 0, 0);
      if (shootDate.getTime() === tomorrow.getTime()) {
        const assignedIds = new Set();
        if (job.photo_assigned_to) assignedIds.add(job.photo_assigned_to);
        (job.stages || []).forEach(s => { if (s.assignedTo) assignedIds.add(s.assignedTo); });
        for (const id of assignedIds) {
          const emp = empById[id];
          if (emp?.phone) {
            const msg = `Hi ${emp.name.split(' ')[0]}! Reminder: you're on "${job.job_name}" tomorrow. Check the app for the itinerary. – Eyecon Moments`;
            results.push({ type: 'shoot_reminder', job: job.job_name, to: emp.phone, ...await sendSMS(emp.phone, msg) });
          }
        }
      }
    }

    // Deadline warning to admin
    if (deadline && process.env.ADMIN_PHONE) {
      deadline.setHours(0, 0, 0, 0);
      const daysLeft = Math.round((deadline - today) / (1000 * 60 * 60 * 24));
      if (daysLeft === 3) {
        const msg = `Eyecon Moments: "${job.job_name}" deadline is in 3 days. Check the app for editing status.`;
        results.push({ type: 'deadline_warning', job: job.job_name, daysLeft, ...await sendSMS(process.env.ADMIN_PHONE, msg) });
      }
    }
  }

  return { statusCode: 200, body: JSON.stringify({ ok: true, sent: results.length, results }) };
};
