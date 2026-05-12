const webPush = require('web-push');

exports.handler = async (event) => {
  const headers = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type',
    'Content-Type': 'application/json',
  };

  if (event.httpMethod === 'OPTIONS') return { statusCode: 204, headers };
  if (event.httpMethod !== 'POST') return { statusCode: 405, headers, body: '{"error":"Method not allowed"}' };

  if (!process.env.VAPID_PUBLIC_KEY || !process.env.VAPID_PRIVATE_KEY) {
    return { statusCode: 500, headers, body: '{"error":"VAPID keys not configured"}' };
  }
  webPush.setVapidDetails('mailto:eyecon.moments@gmail.com', process.env.VAPID_PUBLIC_KEY, process.env.VAPID_PRIVATE_KEY);

  try {
    const { subscriptions, title, body, icon } = JSON.parse(event.body || '{}');
    if (!subscriptions || !subscriptions.length) return { statusCode: 200, headers, body: '{"sent":0}' };

    const payload = JSON.stringify({ title, body, icon: icon || '/logo.png' });

    const results = await Promise.allSettled(
      subscriptions.map(sub => webPush.sendNotification(sub, payload))
    );

    const sent = results.filter(r => r.status === 'fulfilled').length;
    const failed = results.filter(r => r.status === 'rejected').length;

    return { statusCode: 200, headers, body: JSON.stringify({ sent, failed }) };
  } catch (e) {
    console.error('send-push error:', e);
    return { statusCode: 500, headers, body: JSON.stringify({ error: e.message }) };
  }
};
