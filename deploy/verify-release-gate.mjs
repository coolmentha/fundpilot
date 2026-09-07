import { pathToFileURL } from 'node:url'

const REQUIRED_JOBS = ['Backend full test', 'Frontend lint, test and build']

export function verifyReleaseGate({ expectedSha, runs, jobs }) {
  const run = runs.find(candidate => candidate.head_sha === expectedSha)
  if (!run && runs.length) throw new Error(`CI result is from a different commit than ${expectedSha}`)
  if (!run) throw new Error(`missing CI run for ${expectedSha}`)
  if (run.status !== 'completed' || run.conclusion !== 'success') {
    throw new Error(`CI run did not succeed for ${expectedSha}`)
  }
  for (const name of REQUIRED_JOBS) {
    const job = jobs.find(candidate => candidate.name === name)
    if (!job) throw new Error(`missing required CI job: ${name}`)
    if (job.conclusion !== 'success') throw new Error(`required CI job did not succeed: ${name}`)
  }
  return run
}

async function getJson(url, token) {
  const response = await fetch(url, {
    signal: AbortSignal.timeout(15_000),
    headers: {
      accept: 'application/vnd.github+json',
      authorization: `Bearer ${token}`,
      'x-github-api-version': '2022-11-28'
    }
  })
  if (!response.ok) throw new Error(`GitHub API ${response.status}: ${url.pathname}`)
  return response.json()
}

export async function verifyCurrentRelease(env = process.env) {
  const { GITHUB_API_URL = 'https://api.github.com', GITHUB_REPOSITORY, GITHUB_SHA, GITHUB_TOKEN } = env
  if (!/^[0-9a-f]{40}$/.test(GITHUB_SHA ?? '')) throw new Error('GITHUB_SHA must be a full lowercase commit SHA')
  if (!/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(GITHUB_REPOSITORY ?? '')) throw new Error('invalid GITHUB_REPOSITORY')
  if (!GITHUB_TOKEN) throw new Error('GITHUB_TOKEN is required')

  const runsUrl = new URL(`/repos/${GITHUB_REPOSITORY}/actions/workflows/ci.yml/runs`, GITHUB_API_URL)
  runsUrl.searchParams.set('head_sha', GITHUB_SHA)
  runsUrl.searchParams.set('per_page', '100')
  const { workflow_runs: runs = [] } = await getJson(runsUrl, GITHUB_TOKEN)
  const run = runs.find(candidate => candidate.head_sha === GITHUB_SHA)
  const jobs = run
    ? (await getJson(new URL(`/repos/${GITHUB_REPOSITORY}/actions/runs/${run.id}/attempts/${run.run_attempt}/jobs?per_page=100`, GITHUB_API_URL), GITHUB_TOKEN)).jobs ?? []
    : []
  return verifyReleaseGate({ expectedSha: GITHUB_SHA, runs, jobs })
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  verifyCurrentRelease()
    .then(run => console.log(`release gate passed: commit ${run.head_sha}, CI run ${run.id}, attempt ${run.run_attempt}`))
    .catch(error => {
      console.error(`release gate rejected: ${error.message}`)
      process.exitCode = 1
    })
}
