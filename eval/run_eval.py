#!/usr/bin/env python3
"""OA 工单 Agent 评测脚本

逐条调用 /api/agent/ticket，按 cases.json 里定义的规则判定通过与否。

模型输出有随机性，同一条用例跑多次结果可能不同。默认每条跑 1 次，
用 --repeat 3 可以看稳定性——只有 3 次全过才算「稳定通过」。

用法:
    python eval/run_eval.py                          # 全部用例，每条 1 次
    python eval/run_eval.py --repeat 3               # 全部用例，每条 3 次
    python eval/run_eval.py --category adversarial   # 只跑某一类
    python eval/run_eval.py --id C02                 # 只跑一条

前置条件：服务已在 8080 端口启动。
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

BASE_URL = os.environ.get("EVAL_BASE_URL", "http://localhost:8080")
TIMEOUT = 150

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


def post_agent(question):
    """调用一次 Agent 接口，返回 (http_code, body_dict)"""
    payload = json.dumps({"question": question}, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        f"{BASE_URL}/api/agent/ticket",
        data=payload,
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    # 显式清空代理，避免环境变量里的 HTTP_PROXY 把本地请求带走
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(request, timeout=TIMEOUT) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        try:
            return e.code, json.loads(body)
        except json.JSONDecodeError:
            return e.code, {"raw": body}


def judge(case, http_code, body):
    """按 expect 逐项判定，返回失败原因列表（空表示通过）"""
    expect = case["expect"]
    failures = []

    expected_http = expect.get("expectHttp")
    if expected_http and http_code != expected_http:
        failures.append(f"HTTP {http_code}，期望 {expected_http}")
        return failures

    # 非 200 的用例（比如空参数）到这里就够了
    if http_code != 200:
        return failures

    content = body.get("content") or ""
    called = [item.get("toolName") for item in body.get("toolInvocations", [])]
    steps = body.get("steps") or 0

    for tool in expect.get("mustCallTools", []):
        if tool not in called:
            failures.append(f"未调用 {tool}（实际调用 {called or '无'}）")

    for tool in expect.get("mustNotCallTools", []):
        if tool in called:
            failures.append(f"不该调用 {tool}")

    if "anyCallTool" in expect:
        if not any(tool in called for tool in expect["anyCallTool"]):
            failures.append(f"未调用任一工具 {expect['anyCallTool']}（实际 {called or '无'}）")

    for keyword in expect.get("mustContain", []):
        if keyword not in content:
            failures.append(f"回答缺少「{keyword}」")

    if "anyContain" in expect:
        if not any(keyword in content for keyword in expect["anyContain"]):
            failures.append(f"回答缺少任一关键词 {expect['anyContain']}")

    for keyword in expect.get("mustNotContain", []):
        if keyword in content:
            failures.append(f"回答出现禁止内容「{keyword}」")

    max_steps = expect.get("maxSteps")
    if max_steps and steps > max_steps:
        failures.append(f"步数 {steps} 超过上限 {max_steps}")

    return failures


def run_once(case):
    """跑一次，返回本次结果"""
    start = time.time()
    try:
        http_code, body = post_agent(case["question"])
        elapsed = time.time() - start
        failures = judge(case, http_code, body)
        return {
            "passed": not failures,
            "failures": failures,
            "elapsed": round(elapsed, 2),
            "steps": body.get("steps") or 0,
            "tokens": body.get("totalTokens") or 0,
            "stopReason": body.get("stopReason"),
            "content": (body.get("content") or "")[:300],
        }
    except Exception as e:
        return {
            "passed": False,
            "failures": [f"调用异常：{e}"],
            "elapsed": round(time.time() - start, 2),
            "steps": 0,
            "tokens": 0,
            "stopReason": None,
            "content": "",
        }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--category", help="只跑指定类别：normal/boundary/adversarial/error")
    parser.add_argument("--id", help="只跑指定用例编号，如 C02")
    parser.add_argument("--repeat", type=int, default=1, help="每条用例重复跑几次，默认 1")
    parser.add_argument("--no-report", action="store_true", help="不写 Markdown 报告")
    args = parser.parse_args()

    data = json.loads((Path(__file__).parent / "cases.json").read_text(encoding="utf-8"))
    cases = data["cases"]

    if args.category:
        cases = [c for c in cases if c["category"] == args.category]
    if args.id:
        cases = [c for c in cases if c["id"] == args.id]

    if not cases:
        print("没有匹配的用例")
        return

    repeat = max(1, args.repeat)
    print(f"共 {len(cases)} 条用例 × {repeat} 次 = {len(cases) * repeat} 次调用（{BASE_URL}）\n")
    print(f"{'ID':<5} {'类别':<12} {'通过':<6} {'平均耗时':>9} {'平均步数':>9} {'平均token':>10}  首次失败原因")
    print("-" * 100)

    results = []
    for case in cases:
        runs = [run_once(case) for _ in range(repeat)]
        pass_count = sum(1 for r in runs if r["passed"])
        first_failure = next((r["failures"][0] for r in runs if r["failures"]), "")

        results.append({
            "id": case["id"],
            "category": case["category"],
            "question": case["question"],
            "passCount": pass_count,
            "repeat": repeat,
            "stable": pass_count == repeat,
            "avgElapsed": round(sum(r["elapsed"] for r in runs) / repeat, 2),
            "avgSteps": round(sum(r["steps"] for r in runs) / repeat, 1),
            "avgTokens": round(sum(r["tokens"] for r in runs) / repeat),
            "failures": [f for r in runs for f in r["failures"]],
            "content": runs[-1]["content"],
        })

        mark = f"{pass_count}/{repeat}"
        print(f"{case['id']:<5} {case['category']:<12} {mark:<6} "
              f"{results[-1]['avgElapsed']:>8.1f}s {results[-1]['avgSteps']:>9} "
              f"{results[-1]['avgTokens']:>10}  {first_failure[:40]}")

    # ---- 汇总 ----
    total_runs = len(results) * repeat
    total_passes = sum(r["passCount"] for r in results)
    stable = sum(1 for r in results if r["stable"])
    avg_elapsed = sum(r["avgElapsed"] for r in results) / len(results)
    avg_tokens = sum(r["avgTokens"] for r in results) / len(results)

    print("\n" + "=" * 100)
    print(f"逐次通过率: {total_passes}/{total_runs} = {total_passes * 100 / total_runs:.1f}%")
    if repeat > 1:
        print(f"稳定通过率: {stable}/{len(results)} 条用例 {repeat} 次全过 = {stable * 100 / len(results):.1f}%")
    print(f"平均耗时: {avg_elapsed:.1f}s    平均 token: {avg_tokens:.0f}")

    print("\n分类统计（逐次）:")
    for category in ["normal", "boundary", "adversarial", "error"]:
        group = [r for r in results if r["category"] == category]
        if not group:
            continue
        ok = sum(r["passCount"] for r in group)
        runs = len(group) * repeat
        stable_ok = sum(1 for r in group if r["stable"])
        extra = f"  稳定 {stable_ok}/{len(group)}" if repeat > 1 else ""
        print(f"  {category:<12} {ok}/{runs}  {ok * 100 / runs:.0f}%{extra}")

    unstable = [r for r in results if not r["stable"]]
    if unstable:
        print("\n不稳定或失败用例:")
        for r in unstable:
            print(f"  [{r['id']}] {r['passCount']}/{repeat}  {r['question'][:40]}")
            for f in sorted(set(r["failures"]))[:3]:
                print(f"       - {f}")

    if not args.no_report:
        write_report(results, total_passes, total_runs, stable, repeat, avg_elapsed, avg_tokens)


def write_report(results, total_passes, total_runs, stable, repeat, avg_elapsed, avg_tokens):
    """写一份 Markdown 报告，方便贴进仓库"""
    now = datetime.now().strftime("%Y-%m-%d %H:%M")
    lines = [
        f"# 评测报告 {now}",
        "",
        f"- 用例数：{len(results)}",
        f"- 每条运行次数：{repeat}",
        f"- 逐次通过率：{total_passes}/{total_runs}（{total_passes * 100 / total_runs:.1f}%）",
    ]
    if repeat > 1:
        lines.append(f"- 稳定通过：{stable}/{len(results)}（{repeat} 次全过）")
    lines += [
        f"- 平均耗时：{avg_elapsed:.1f}s",
        f"- 平均 token：{avg_tokens:.0f}",
        "",
        "## 分类结果",
        "",
        "| 类别 | 逐次通过 | 通过率 | 稳定通过 |",
        "|---|---|---|---|",
    ]
    for category in ["normal", "boundary", "adversarial", "error"]:
        group = [r for r in results if r["category"] == category]
        if not group:
            continue
        ok = sum(r["passCount"] for r in group)
        runs = len(group) * repeat
        stable_ok = sum(1 for r in group if r["stable"])
        lines.append(f"| {category} | {ok}/{runs} | {ok * 100 / runs:.0f}% | {stable_ok}/{len(group)} |")

    lines += ["", "## 用例明细", "",
              f"| ID | 类别 | 通过 | 平均耗时 | 平均步数 | 平均 token | 失败原因 |",
              "|---|---|---|---|---|---|---|"]
    for r in results:
        reason = "；".join(sorted(set(r["failures"]))[:2]) if r["failures"] else "-"
        lines.append(f"| {r['id']} | {r['category']} | {r['passCount']}/{repeat} | "
                     f"{r['avgElapsed']}s | {r['avgSteps']} | {r['avgTokens']} | {reason} |")

    report_path = Path(__file__).parent / f"report-{datetime.now().strftime('%Y%m%d-%H%M')}.md"
    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"\n报告已写入：{report_path}")


if __name__ == "__main__":
    main()
