#!/usr/bin/env bash
set -Eeuo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
write_env=$(sed -n '/^            write_env() {$/,/^            }$/p' \
  "$repo_root/.github/workflows/deploy.yml" | sed 's/^            //')
[ -n "$write_env" ]

test_root=$(mktemp -d)
trap 'rm -rf "$test_root"' EXIT

for fixture in missing unreadable; do
  case_dir="$test_root/$fixture"
  mkdir -p "$case_dir"
  [ "$fixture" = missing ] || mkdir "$case_dir/.env"

  set +e
  (
    cd "$case_dir"
    unset YANGJIBAO_SECRET
    eval "$write_env"
    set -Eeuo pipefail
    write_env v0.0.0
  ) 2>"$case_dir/stderr"
  status=$?
  set -e

  [ "$status" -ne 0 ]
  grep -Fxq 'YANGJIBAO_SECRET is required in GitHub Secrets or VPS .env' "$case_dir/stderr"
  [ ! -e "$case_dir/.env.tmp" ]
done

for fixture in github legacy override; do
  case_dir="$test_root/$fixture"
  mkdir -p "$case_dir"
  (
    cd "$case_dir"
    DB_USERNAME=test DB_PASSWORD=test FUNDPILOT_SESSION_SECRET=test
    FUNDPILOT_BOOTSTRAP_USERNAME=test FUNDPILOT_BOOTSTRAP_PASSWORD=test
    if [ "$fixture" != github ]; then
      printf 'YANGJIBAO_SECRET=test-only-legacy\n' > .env
    fi
    if [ "$fixture" = legacy ]; then
      unset YANGJIBAO_SECRET
      expected=test-only-legacy
    else
      export YANGJIBAO_SECRET=test-only-github
      expected=test-only-github
    fi
    eval "$write_env"
    write_env v0.0.0
    grep -Fxq "YANGJIBAO_SECRET=$expected" .env
    [ ! -e .env.tmp ]
  )
done
