exports.handler = async (event) => {
  const headers = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type',
    'Content-Type': 'application/json',
  };

  if (event.httpMethod === 'OPTIONS') return { statusCode: 204, headers };
  if (event.httpMethod !== 'POST') return { statusCode: 405, headers, body: '{"error":"Method not allowed"}' };

  if (!process.env.ANTHROPIC_API_KEY) {
    return { statusCode: 500, headers, body: '{"error":"ANTHROPIC_API_KEY not configured"}' };
  }

  try {
    const payload = JSON.parse(event.body || '{}');
    const apiHeaders = {
      'Content-Type': 'application/json',
      'x-api-key': process.env.ANTHROPIC_API_KEY,
      'anthropic-version': '2023-06-01',
    };
    // Interleaved thinking is required when combining extended thinking with tool use
    if (payload.thinking && payload.tools) {
      apiHeaders['anthropic-beta'] = 'interleaved-thinking-2025-05-14';
    }
    const res = await fetch('https://api.anthropic.com/v1/messages', {
      method: 'POST',
      headers: apiHeaders,
      body: JSON.stringify(payload),
    });
    const data = await res.json();
    return { statusCode: res.status, headers, body: JSON.stringify(data) };
  } catch (e) {
    return { statusCode: 500, headers, body: JSON.stringify({ error: e.message }) };
  }
};
