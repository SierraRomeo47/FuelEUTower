"""Generate params.json from FuelEU flexibility workbook (optional local tooling)."""
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
        print("Usage: analyze_params.py [path/to/FuelEU_Flexibility_Workbook_Ready.xlsx]", file=sys.stderr)
        sys.exit(1)

    xl = pd.ExcelFile(file_path)
    out = {}
    df_params = xl.parse("Regulatory_Parameters").dropna(how="all")
    out["Regulatory_Parameters"] = df_params.to_dict(orient="records")
    df_rules = xl.parse("Rule_Assumption_Register").dropna(how="all")
    out["Rules"] = df_rules.to_dict(orient="records")

    out_path = REG_DIR / "params.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2, default=str)
    print(f"Wrote {out_path}")


if __name__ == "__main__":
    main()
