# Issue tracker: GitHub

Issues and PRDs for this repo live as GitHub issues in `coolmentha/fundpilot`. Use the `gh` CLI for all operations.

## Conventions

- **Create an issue**: `gh issue create --title "..." --body "..."`. Use a heredoc for multi-line bodies.
- **Read an issue**: `gh issue view <number> --comments`, filtering comments by `jq` and also fetching labels.
- **List issues**: `gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'` with appropriate `--label` and `--state` filters.
- **Comment on an issue**: `gh issue comment <number> --body "..."`
- **Apply / remove labels**: `gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- **Close**: `gh issue close <number> --comment "..."`

Infer the repo from `git remote -v` — `gh` does this automatically when run inside a clone.

## When a skill says "publish to the issue tracker"

Create a GitHub issue.

## When a skill says "fetch the relevant ticket"

Run `gh issue view <number> --comments`.

## 本仓库适配备注

- Remote: `https://github.com/coolmentha/fundpilot.git`，`gh` 在 clone 内会自动识别 owner/repo。
- 若 `gh` 未安装或未登录（`gh auth status` 失败），相关技能在此环境无法直接发布。请先安装 GitHub CLI 并执行 `gh auth login`，或临时把 PRD/issue 内容贴到 GitHub 网页 UI。
- 标签首次使用时若仓库尚未创建（除 `wontfix` 外四个），可通过 `gh label create needs-triage`、`gh label create ready-for-agent` 等命令一次性补齐。
