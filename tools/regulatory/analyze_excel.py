"""Dump first-row column names per sheet (optional local tooling)."""
import json
import sys
from pathlib import Path

import pandas as pd

REG_DIR = Path(__file__).resolve().parent
DEFAULT_XLSX = REG_DIR / "FuelEU_Flexibility_Workbook_Ready.xlsx"


def main() -> None:
    file_path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_XLSX
    if not file_path.is_file():
        print(f"Workbook not found: {file_path}", file=sys.stderr)
        print("Usage: analyze_excel.py [path/to/workbook.xlsx]", file=sys.stderr)
        sys.exit(1)

    xl = pd.ExcelFile(file_path)
    output = {}
    for sheet in xl.sheet_names:
        df = xl.parse(sheet, nrows=5)
        output[sheet] = [str(c) for c in df.columns]

    out_path = REG_DIR / "excel_schema.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2)
    print(f"Wrote {out_path}")


if __name__ == "__main__":
    main()
