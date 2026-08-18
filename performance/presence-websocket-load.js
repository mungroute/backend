import ws from 'k6/ws'
import { check, fail, sleep } from 'k6'
import { SharedArray } from 'k6/data'
import { Counter, Rate, Trend } from 'k6/metrics'

const targetVus = Number(__ENV.TARGET_VUS || 500)
const smoke = __ENV.SMOKE === 'true'
const fixturePath = __ENV.PRESENCE_FIXTURES || './presence-fixtures.json'
const fixtures = new SharedArray('websocket presence sessions', () => JSON.parse(open(fixturePath)))
const holdSeconds = Number(__ENV.WS_HOLD_SECONDS || (smoke ? 15 : 150))
const sendEverySeconds = Number(__ENV.WS_SEND_INTERVAL_SECONDS || 4)
const rampSeconds = Number(__ENV.WS_RAMP_SECONDS || (smoke ? 3 : 45))

if (fixtures.length < targetVus && __ENV.ALLOW_FIXTURE_REUSE !== 'true') {
  fail(`Need at least ${targetVus} distinct presence fixtures; received ${fixtures.length}.`)
}

const websocketConnectDuration = new Trend('presence_ws_connect_duration', true)
const websocketRoundTrip = new Trend('presence_ws_round_trip', true)
const websocketSessionFailed = new Rate('presence_ws_session_failed')
const websocketPrivacyFailed = new Rate('presence_ws_privacy_failed')
const websocketMessages = new Counter('presence_ws_messages')

const functionalThresholds = {
  presence_ws_session_failed: ['rate<0.01'],
  presence_ws_privacy_failed: ['rate==0'],
}

export const options = {
  scenarios: {
    presence_websocket: {
      executor: 'per-vu-iterations',
      vus: targetVus,
      iterations: 1,
      maxDuration: `${Math.ceil(rampSeconds + holdSeconds + 30)}s`,
    },
  },
  thresholds: smoke ? functionalThresholds : {
    ...functionalThresholds,
    presence_ws_connect_duration: ['p(95)<1000', 'p(99)<2000'],
    presence_ws_round_trip: ['p(95)<500', 'p(99)<1000'],
  },
}

const frame = (command, headers = {}, body = '') => {
  const headerLines = Object.entries(headers).map(([key, value]) => `${key}:${value}`)
  return `${command}\n${headerLines.join('\n')}\n\n${body}\0`
}

const commandOf = (message) => message.replace(/^\n+/, '').split('\n', 1)[0]

const bodyOf = (message) => {
  const separator = message.indexOf('\n\n')
  return JSON.parse(message.slice(separator + 2).replace(/\0$/, ''))
}

const privacySafe = (payload) => Array.isArray(payload.nearby) && payload.nearby.every((item) => (
  item.userId === undefined
  && item.sessionId === undefined
  && item.lon === undefined
  && item.lat === undefined
  && item.latitude === undefined
  && item.longitude === undefined
  && item.distanceM === undefined
  && item.distanceMeters === undefined
))

export default function () {
  const fixture = fixtures[(__VU - 1) % fixtures.length]
  if (rampSeconds > 0 && targetVus > 1) {
    sleep(((__VU - 1) / (targetVus - 1)) * rampSeconds)
  }
  const startedAt = Date.now()
  let connected = false
  let messages = 0
  let lastSentAt = 0
  let failed = false

  const sendPresence = (socket) => {
    lastSentAt = Date.now()
    const phase = (__ITER % 12) / 100000
    socket.send(frame('SEND', {
      destination: '/app/presence',
      'content-type': 'application/json',
    }, JSON.stringify({
      sessionId: fixture.sessionId,
      measuredAt: new Date().toISOString(),
      lon: fixture.lon + phase,
      lat: fixture.lat + phase,
      accuracy: 7,
      heading: (__ITER * 15) % 360,
      stationary: __ITER % 5 === 0,
      radiusM: 100,
    })))
  }

  const response = ws.connect(__ENV.WS_URL || 'ws://localhost:8080/ws', {}, (socket) => {
    socket.on('open', () => {
      socket.send(frame('CONNECT', {
        'accept-version': '1.2',
        'heart-beat': '0,0',
        Authorization: `Bearer ${fixture.accessToken}`,
      }))
    })

    socket.on('message', (message) => {
      const command = commandOf(message)
      if (command === 'CONNECTED') {
        connected = true
        websocketConnectDuration.add(Date.now() - startedAt)
        socket.send(frame('SUBSCRIBE', {
          id: `presence-load-${__VU}`,
          destination: '/user/queue/presence',
          ack: 'auto',
        }))
        sendPresence(socket)
        socket.setInterval(() => sendPresence(socket), sendEverySeconds * 1000)
        return
      }

      if (command === 'MESSAGE') {
        const payload = bodyOf(message)
        messages += 1
        websocketMessages.add(1)
        if (lastSentAt > 0) websocketRoundTrip.add(Date.now() - lastSentAt)
        const safe = privacySafe(payload)
        websocketPrivacyFailed.add(!safe)
        if (!safe) failed = true
        return
      }

      if (command === 'ERROR') {
        failed = true
        socket.close()
      }
    })

    socket.on('error', () => { failed = true })
    socket.setTimeout(() => {
      socket.close()
    }, holdSeconds * 1000)
  })

  const handshakeOk = response?.status === 101
  check(response, { 'WebSocket handshake returns 101': () => handshakeOk })
  failed ||= !handshakeOk || !connected || messages === 0
  websocketSessionFailed.add(failed)
}
