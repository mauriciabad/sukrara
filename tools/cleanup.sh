#!/bin/bash
# Sukrara — https://github.com/mauriciabad/sukrara
# Copyright (C) 2026 Maurici Abad
# SPDX-License-Identifier: GPL-3.0-or-later
# Removes the worktrees and local branches whose work is already on origin/next or origin/main (docs/DEVELOPMENT.md
# "Cleanup"). The session that started agents runs it once their work is on next (CLAUDE.md "Whoever creates…").
#   tools/cleanup.sh [--dry-run] [--quiet] [--fetch] [--idle MINUTES]
# Lossless: it never touches main, next, the current directory's worktree, a branch checked out in a kept worktree, a
# locked worktree, or one with changes or untracked files. A worktree goes only when its HEAD's commits are all on
# origin/next or origin/main (the same commit or the same patch) and nothing in it changed for --idle minutes (default
# 240: an agent waiting on CI after its push keeps its worktree). A branch goes on the same test. A squash-merged
# branch has commits that aren't on next one by one: it stays and is listed; delete it by hand once checked.
# --fetch updates origin first (otherwise the refs as last fetched: never more deleted than they allow).
set -u
dry=0 quiet=0 fetch=0 idle=240
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) dry=1 ;;
    --quiet) quiet=1 ;;
    --fetch) fetch=1 ;;
    --idle) shift; idle="${1:?--idle needs minutes}" ;;
    -h | --help) sed -n '5,13p' "$0"; exit 0 ;;
    *) echo "unknown argument '$1'" >&2; exit 2 ;;
  esac
  shift
done

say() { [ "$quiet" = 1 ] || echo "$@"; }
run() { if [ "$dry" = 1 ]; then say "would: $*"; else "$@"; fi; }

top="$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "not in a git checkout" >&2; exit 2; }
main_wt="$(git worktree list --porcelain | sed -n '1s/^worktree //p')"
[ "$fetch" = 1 ] && git fetch -q origin
homes=""
for r in origin/next origin/main; do git rev-parse -q --verify "$r^{commit}" >/dev/null && homes="$homes $r"; done
[ -n "$homes" ] || { echo "no origin/next or origin/main: nothing is known to be safe" >&2; exit 2; }

# 0 when every non-merge commit of $1 is on a home branch, as the same commit or the same patch.
landed() {
  for h in $homes; do
    git merge-base --is-ancestor "$1" "$h" 2>/dev/null && return 0
  done
  for h in $homes; do
    [ -z "$(git log --format=%H --no-merges --cherry-pick --right-only "$h...$1" 2>/dev/null)" ] && return 0
  done
  return 1
}

# 0 when nothing in worktree $1 (its git dir's index, HEAD, logs) changed in the last $idle minutes.
idle_wt() {
  gd="$(git -C "$1" rev-parse --absolute-git-dir 2>/dev/null)" || return 1
  [ -z "$(find "$gd/index" "$gd/HEAD" "$gd/logs/HEAD" -maxdepth 0 -mmin "-$idle" 2>/dev/null)" ]
}

removed=0 kept=""
here="$(cd "$top" && pwd -P)"
in_use=" main next "
wt="" head="" branch="" locked=0
flush_wt() {
  [ -n "$wt" ] || return 0
  if [ "$wt" = "$main_wt" ] || [ "$(cd "$wt" 2>/dev/null && pwd -P)" = "$here" ]; then
    [ -n "$branch" ] && in_use="$in_use$branch "
  elif [ ! -d "$wt" ]; then
    : # gone from disk: prune below
  elif [ "$locked" = 1 ]; then
    kept="$kept\n  worktree $wt: locked"; [ -n "$branch" ] && in_use="$in_use$branch "
  elif [ -n "$(git -C "$wt" status --porcelain 2>/dev/null)" ]; then
    kept="$kept\n  worktree $wt: has changes"; [ -n "$branch" ] && in_use="$in_use$branch "
  elif ! landed "$head"; then
    kept="$kept\n  worktree $wt: commits not on next/main"; [ -n "$branch" ] && in_use="$in_use$branch "
  elif ! idle_wt "$wt"; then
    kept="$kept\n  worktree $wt: used in the last $idle min"; [ -n "$branch" ] && in_use="$in_use$branch "
  else
    say "worktree $wt"
    run git worktree remove "$wt" && removed=$((removed + 1))
  fi
  wt="" head="" branch="" locked=0
}
while IFS= read -r line; do
  case "$line" in
    "worktree "*) flush_wt; wt="${line#worktree }" ;;
    "HEAD "*) head="${line#HEAD }" ;;
    "branch refs/heads/"*) branch="${line#branch refs/heads/}" ;;
    locked*) locked=1 ;;
  esac
done <<EOF
$(git worktree list --porcelain)
EOF
flush_wt
run git worktree prune

for b in $(git for-each-ref --format='%(refname:short)' refs/heads); do
  case "$in_use" in *" $b "*) continue ;; esac
  if landed "refs/heads/$b"; then
    say "branch $b"
    run git branch -D -q "$b" && removed=$((removed + 1))
  else
    kept="$kept\n  branch $b: commits not on next/main"
  fi
done

say "removed $removed"
[ -n "$kept" ] && say "kept:$(printf '%b' "$kept")"
exit 0
