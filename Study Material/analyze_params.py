import pandas as pd
import json

file_path = "FuelEU_Flexibility_Workbook_Ready.xlsx"
xl = pd.ExcelFile(file_path)

out = {}
# Read parameters, dropping completely empty rows/columns to keep it clean
df_params = xl.parse("Regulatory_Parameters").dropna(how='all')
out["Regulatory_Parameters"] = df_params.to_dict(orient="records")

df_rules = xl.parse("Rule_Assumption_Register").dropna(how='all')
out["Rules"] = df_rules.to_dict(orient="records")

with open("params.json", "w") as f:
    json.dump(out, f, indent=2, default=str)
