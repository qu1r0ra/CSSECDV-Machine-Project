# Issue tracker: GitHub

Issues and specs live in GitHub Issues for `qu1r0ra/CSSECDV-Machine-Project`. Use the `gh` CLI. Run commands inside this repository's checkout, or pass `--repo qu1r0ra/CSSECDV-Machine-Project` explicitly.

## Conventions

- Create: `gh issue create --title "..." --body-file <path>`.
- Read: `gh issue view <number> --json number,title,body,labels,comments`.
- List: `gh issue list --state open --json number,title,body,labels,comments`, with label and state filters as needed.
- Update the body: `gh issue edit <number> --body-file <path>`.
- Comment: `gh issue comment <number> --body-file <path>`.
- Label: `gh issue edit <number> --add-label "..."` or `--remove-label "..."`.
- Close: `gh issue close <number> --comment "..."`.

For multiline bodies and comments, write the exact Markdown to a temporary UTF-8 file and pass its path with `--body-file`.

When a skill says "publish to the issue tracker", create a GitHub issue. When it says "fetch the relevant ticket", read that issue and its comments.

## Pull requests as a triage surface

**PRs as a request surface: no.**

GitHub shares numbers between issues and pull requests. Resolve an ambiguous reference with `gh pr view <number>`, falling back to `gh issue view <number>`.
