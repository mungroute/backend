import ws from 'k6/ws';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const smokeFailures = new Counter('presence_websocket_smoke_failures');
const fixtures = JSON.parse(open(__ENV.FIXTURE_PATH || './presence-fixtures.json'));
const fixture = Array.isArray(fixtures) ? fixtures[0] : fixtures;

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    presence_websocket_smoke_failures: ['count==0'],
  },
};

const frame = (command, headers = {}, body = '') => {
  const headerLines = Object.entries(headers).map(([key, value]) => `${key}:${value}`);
  return `${command}\n${headerLines.join('\n')}\n\n${body}\0`;
};

const commandOf = (message) => message.replace(/^\n+/, '').split('\n', 1)[0];

export default function () {
  let completed = false;
  const response = ws.connect(__ENV.WS_URL || 'ws://localhost:8080/ws', {}, (socket) => {
    socket.on('open', () => {
      socket.send(frame('CONNECT', {
        'accept-version': '1.2',
        'heart-beat': '0,0',
        Authorization: `Bearer ${fixture.accessToken}`,
      }));
    });

    socket.on('message', (message) => {
      const command = commandOf(message);
      if (command === 'CONNECTED') {
        socket.send(frame('SUBSCRIBE', {
          id: 'presence-smoke',
          destination: '/user/queue/presence',
          ack: 'auto',
        }));
        socket.send(frame('SEND', {
          destination: '/app/presence',
          'content-type': 'application/json',
        }, JSON.stringify({
          sessionId: fixture.sessionId,
          measuredAt: new Date().toISOString(),
          lon: fixture.lon,
          lat: fixture.lat,
          accuracy: 7,
          heading: 90,
          stationary: false,
          radiusM: 100,
        })));
        return;
      }

      if (command === 'MESSAGE') {
        const separator = message.indexOf('\n\n');
        const payload = JSON.parse(message.slice(separator + 2).replace(/\0$/, ''));
        if (!check(payload, {
          'WebSocket presence response has the session': (body) => body.sessionId === fixture.sessionId,
          'WebSocket presence response preserves privacy': (body) => body.nearby.every((item) =>
            item.userId === undefined && item.lon === undefined && item.lat === undefined && item.distanceM === undefined),
        })) {
          smokeFailures.add(1);
        }
        completed = true;
        socket.close();
        return;
      }

      if (command === 'ERROR') {
        smokeFailures.add(1);
        socket.close();
      }
    });

    socket.on('error', () => {
      smokeFailures.add(1);
    });

    socket.setTimeout(() => {
      if (!completed) smokeFailures.add(1);
      socket.close();
    }, 10_000);
  });

  if (!check(response, { 'WebSocket handshake returns 101': (result) => result?.status === 101 })) {
    smokeFailures.add(1);
  }
}
