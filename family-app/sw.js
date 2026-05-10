self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', e => e.waitUntil(clients.claim()));

// Receive scheduled prayer times from the page and set timeouts here
// so notifications fire even when the browser tab is backgrounded on Android
self.addEventListener('message', e => {
  if (!e.data || e.data.type !== 'PRAYER_SCHEDULE') return;
  e.data.prayers.forEach(({ key, label, icon, time, delayMs }) => {
    setTimeout(() => {
      self.registration.showNotification(icon + ' ' + label, {
        body: 'It is ' + time + ' — time for ' + label + '. Allahu Akbar.',
        tag: 'prayer-' + key,
        renotify: true,
        vibrate: [300, 100, 300, 100, 300],
        requireInteraction: false,
      });
    }, delayMs);
  });
});

self.addEventListener('notificationclick', e => {
  e.notification.close();
  e.waitUntil(clients.openWindow('/'));
});
