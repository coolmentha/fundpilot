import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'

import { REQUIRED_JOBS } from './verify-release-gate.mjs'

const ci = await readFile(new URL('../.github/workflows/ci.yml', import.meta.url), 'utf8')
const deploy = await readFile(new URL('../.github/workflows/deploy.yml', import.meta.url), 'utf8')

test('所有 Actions 固定到不可变提交', () => {
  const uses = [...`${ci}\n${deploy}`.matchAll(/^\s*-?\s*uses:\s*([^\s#]+)/gm)].map(match => match[1])
  assert.ok(uses.length > 0)
  assert.deepEqual(uses.filter(value => !/@[0-9a-f]{40}$/.test(value)), [])
})

test('CI 名称和发布门禁锁定同一提交的完整验证', () => {
  // 门禁按 job 显示名匹配,这里同时锁定门禁清单与 ci.yml 实际产出的 job:两边改名必须一起改。
  assert.deepEqual(REQUIRED_JOBS, [
    'Backend test (shard-1)',
    'Backend test (shard-2)',
    'Backend test (shard-3)',
    'Backend test (shard-4)',
    'Backend test (shard-5)',
    'Backend test (shard-6)',
    'Backend coverage gate',
    'Frontend lint, test and build'
  ])
  assert.match(ci, /name: Backend test \(\$\{\{ matrix\.shard \}\}\)/)
  assert.match(ci, /matrix:[\s\S]*?shard: \[ shard-1, shard-2, shard-3, shard-4, shard-5, shard-6 \]/)
  assert.match(ci, /name: Backend coverage gate/)
  assert.match(ci, /name: Frontend lint, test and build/)
  assert.match(ci, /run: npm run test:coverage/)
  assert.match(deploy, /name: Require same-commit CI[\s\S]*run: node deploy\/verify-release-gate\.mjs/)
  assert.equal((deploy.match(/needs: \[verify-tag-on-main, release-gate\]/g) ?? []).length, 2)
})

test('候选验证先于正常切换且正式健康失败不会宣称成功', () => {
  const candidate = deploy.indexOf('validate_candidate_backend')
  const switchToRelease = deploy.indexOf('git checkout --detach "$RELEASE_TAG"', candidate)
  const finalHealth = deploy.indexOf('wait_for_release', switchToRelease)
  const success = deploy.indexOf('echo "deployed $RELEASE_TAG ($IMAGE_TAG)"', finalHealth)
  assert.ok(candidate >= 0 && candidate < switchToRelease && switchToRelease < finalHealth && finalHealth < success)
  assert.match(deploy, /if ! TAG="\$IMAGE_TAG"[\s\S]*\|\| ! wait_for_release; then[\s\S]*post_commit_abort/)
})
