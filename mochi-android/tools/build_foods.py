"""
文部科学省「日本食品標準成分表（八訂）増補2023年」の本表(Excel)から、
アプリで使う食品データ app/src/main/assets/foods.tsv を作る。

    curl -L -o honhyo.xlsx https://www.mext.go.jp/content/20260327-mxt_kagsei-mext-000029402_02.xlsx
    python3 tools/build_foods.py honhyo.xlsx

出典：文部科学省「日本食品標準成分表（八訂）増補2023年」
（https://www.mext.go.jp/a_menu/syokuhinseibun/mext_00001.html）を加工して作成
"""
import os
import re
import sys

import openpyxl

COLS = {  # 成分識別子 → 出力名
    "ENERC_KCAL": "kcal",
    "PROT-": "p",
    "FAT-": "f",
    "CHOCDF-": "c",
    "FIB-": "fib",
    "NACL_EQ": "salt",
}


def num(v):
    """「(1.2)」「Tr」「(0)」「-」「*」などを数値にする。推定値の括弧は外す。"""
    if v is None:
        return 0.0
    s = str(v).strip().replace("(", "").replace(")", "")
    if s in ("", "-", "*", "Tr", "(Tr)"):
        return 0.0
    try:
        return float(s)
    except ValueError:
        return 0.0


def main(path):
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    ws = wb["表全体"]
    rows = list(ws.iter_rows(values_only=True))
    ids = [str(x).strip() if x else "" for x in rows[11]]
    idx = {name: ids.index(code) for code, name in COLS.items()}
    out = ["id\tgroup\tname\tkcal\tp\tf\tc\tfib\tsalt"]
    for r in rows[12:]:
        if not r or not r[1]:
            continue
        name = re.sub(r"[　 ]+", " ", str(r[3])).strip()
        vals = [num(r[idx[k]]) for k in ("kcal", "p", "f", "c", "fib", "salt")]
        out.append("\t".join([str(r[1]).strip(), str(r[0]).strip(), name] + [f"{v:g}" for v in vals]))
    here = os.path.dirname(os.path.abspath(__file__))
    dst = os.path.join(here, "..", "app", "src", "main", "assets", "foods.tsv")
    with open(dst, "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")
    print(f"{len(out) - 1} foods -> {dst}")


if __name__ == "__main__":
    main(sys.argv[1])
