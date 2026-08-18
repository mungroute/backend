import http from 'k6/http'
import { check, fail, sleep } from 'k6'
import { SharedArray } from 'k6/data'
import { Counter, Rate, Trend } from 'k6/metrics'

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080'
const targetVus = Number(__ENV.TARGET_VUS || 500)
const smoke = __ENV.SMOKE === 'true'
const directRamp = __ENV.DIRECT_RAMP === 'true'
const fixturePath = __ENV.PRESENCE_FIXTURES || './presence-fixtures.json'
const fixtures = new SharedArray('presence sessions', () => JSON.parse(open(fixturePath)))

if (fixtures.length < targetVus && __ENV.ALLOW_FIXTURE_REUSE !== 'true') {
  fail(`Need at least ${targetVus} distinct presence fixtures; received ${fixtures.length}.`)
}

const functionalThresholds = {
  http_req_failed: ['rate<0.01'],
  privacy_contract_failed: ['rate==0'],
}

export const options = {
  scenarios: {
    presence_preflight: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: smoke ? [
        { duration: '5s', target: targetVus },
        { duration: '15s', target: targetVus },
        { duration: '5s', target: 0 },
      ] : directRamp ? [
        { duration: __ENV.REST_RAMP_DURATION || '1m', target: targetVus },
        { duration: __ENV.REST_HOLD_DURATION || '2m', target: targetVus },
        { duration: __ENV.REST_RAMP_DOWN_DURATION || '30s', target: 0 },
      ] : [
        { duration: '30s', target: Math.min(50, targetVus) },
        { duration: '1m', target: Math.min(50, targetVus) },
        { duration: '30s', target: Math.min(100, targetVus) },
        { duration: '1m', target: Math.min(100, targetVus) },
        { duration: '1m', target: targetVus },
        { duration: '2m', target: targetVus },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '15s',
    },
  },
  thresholds: smoke ? functionalThresholds : {
    ...functionalThresholds,
    presence_update_duration: ['p(95)<300', 'p(99)<700'],
  },
}

const presenceUpdateDuration = new Trend('presence_update_duration', true)
const privacyContractFailed = new Rate('privacy_contract_failed')
const presenceHttpErrors = new Counter('presence_http_errors')

const hasNoSensitiveNearbyFields = (body) => {
  try {
    const payload = JSON.parse(body)
    return Array.isArray(payload.nearby) && payload.nearby.every((nearby) => (
      nearby.lat === undefined
      && nearby.lon === undefined
      && nearby.latitude === undefined
      && nearby.longitude === undefined
      && nearby.userId === undefined
      && nearby.sessionId === undefined
      && nearby.distanceMeters === undefined
    ))
  } catch (_) {
    return false
  }
}

export default function () {
  const fixture = fixtures[(__VU - 1) % fixtures.length]
  const phase = (__ITER % 12) / 100000
  const payload = JSON.stringify({
    sessionId: fixture.sessionId,
    measuredAt: new Date().toISOString(),
    lon: fixture.lon + phase,
    lat: fixture.lat + phase,
    accuracy: 7,
    heading: (__ITER * 15) % 360,
    stationary: __ITER % 5 === 0,
    radiusM: 100,
  })
  const response = http.put(`${baseUrl}/api/presence`, payload, {
    headers: {
      Authorization: `Bearer ${fixture.accessToken}`,
      'Content-Type': 'application/json',
    },
    tags: { name: 'presence.update' },
  })

  presenceUpdateDuration.add(response.timings.duration)
  const requestSucceeded = response.status === 200
  const privacySafe = requestSucceeded ? hasNoSensitiveNearbyFields(response.body) : true
  if (requestSucceeded) privacyContractFailed.add(!privacySafe)
  if (!requestSucceeded) presenceHttpErrors.add(1, { status: String(response.status) })
  check(response, {
    'presence update is 200': () => requestSucceeded,
    'nearby response hides exact identity and location': () => privacySafe,
  })
  sleep(response.status === 200 && response.json('nextUpdateAfterSeconds') || 4)
}
