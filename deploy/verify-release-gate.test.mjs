import assert from 'node:assert/strict'
import { test } from 'node:test'

import { verifyCurrentRelease, verifyReleaseGate } from './verify-release-gate.mjs'

test('拒绝缺失的同提交 CI 结果', () => {
  assert.throws(
    () => verifyReleaseGate({ expectedSha: 'a'.repeat(40), runs: [], jobs: [] }),
    /missing CI run/
  )
})

test('拒绝失败的同提交 CI 结果', () => {
  const sha = 'b'.repeat(40)
  assert.throws(
    () => verifyReleaseGate({
      expectedSha: sha,
      runs: [{ id: 7, head_sha: sha, status: 'completed', conclusion: 'failure' }],
      jobs: []
    }),
    /CI run did not succeed/
  )
})

test('拒绝来自不同提交的成功 CI 结果', () => {
  assert.throws(
    () => verifyReleaseGate({
      expectedSha: 'c'.repeat(40),
      runs: [{ id: 8, head_sha: 'd'.repeat(40), status: 'completed', conclusion: 'success' }],
      jobs: []
    }),
    /different commit/
  )
})

test('拒绝缺失任一规定验证结果', () => {
  const sha = 'e'.repeat(40)
  assert.throws(
    () => verifyReleaseGate({
      expectedSha: sha,
      runs: [{ id: 9, head_sha: sha, status: 'completed', conclusion: 'success' }],
      jobs: [{ name: 'Backend full test', conclusion: 'success' }]
    }),
    /missing required CI job: Frontend lint, test and build/
  )
})

test('拒绝任一规定验证失败', () => {
  const sha = 'f'.repeat(40)
  assert.throws(
    () => verifyReleaseGate({
      expectedSha: sha,
      runs: [{ id: 10, head_sha: sha, status: 'completed', conclusion: 'success' }],
      jobs: [
        { name: 'Backend full test', conclusion: 'success' },
        { name: 'Frontend lint, test and build', conclusion: 'failure' }
      ]
    }),
    /required CI job did not succeed: Frontend lint, test and build/
  )
})

test('接受同一提交的全部规定验证成功', () => {
  const sha = '1'.repeat(40)
  assert.equal(verifyReleaseGate({
    expectedSha: sha,
    runs: [{ id: 11, head_sha: sha, status: 'completed', conclusion: 'success' }],
    jobs: [
      { name: 'Backend full test', conclusion: 'success' },
      { name: 'Frontend lint, test and build', conclusion: 'success' }
    ]
  }).id, 11)
})

test('GitHub API 请求使用有界超时并绑定同一 CI attempt', async () => {
  const sha = '2'.repeat(40)
  const originalFetch = globalThis.fetch
  const originalTimeout = AbortSignal.timeout
  const signals = [{ request: 1 }, { request: 2 }]
  const requests = []
  let timeoutCalls = 0

  AbortSignal.timeout = milliseconds => {
    assert.equal(milliseconds, 15_000)
    return signals[timeoutCalls++]
  }
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    return {
      ok: true,
      json: async () => requests.length === 1
        ? { workflow_runs: [{ id: 12, run_attempt: 3, head_sha: sha, status: 'completed', conclusion: 'success' }] }
        : { jobs: [
            { name: 'Backend full test', conclusion: 'success' },
            { name: 'Frontend lint, test and build', conclusion: 'success' }
          ] }
    }
  }

  try {
    await verifyCurrentRelease({
      GITHUB_API_URL: 'https://api.github.test',
      GITHUB_REPOSITORY: 'fundpilot/fundpilot',
      GITHUB_SHA: sha,
      GITHUB_TOKEN: 'test-token'
    })
    assert.equal(timeoutCalls, 2)
    assert.equal(requests[0].options.signal, signals[0])
    assert.equal(requests[1].options.signal, signals[1])
    assert.equal(requests[0].url.pathname, '/repos/fundpilot/fundpilot/actions/workflows/ci.yml/runs')
    assert.equal(requests[0].url.searchParams.get('head_sha'), sha)
    assert.equal(requests[0].url.searchParams.get('per_page'), '100')
    assert.equal(requests[1].url.pathname, '/repos/fundpilot/fundpilot/actions/runs/12/attempts/3/jobs')
    assert.equal(requests[1].url.searchParams.get('per_page'), '100')
  } finally {
    globalThis.fetch = originalFetch
    AbortSignal.timeout = originalTimeout
  }
})
