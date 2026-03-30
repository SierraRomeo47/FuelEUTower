import pandas as pd
import json

file_path = "FuelEU_Flexibility_Workbook_Ready.xlsx"
xl = pd.ExcelFile(file_path)

output = {}
for sheet in xl.sheet_names:
    df = xl.parse(sheet, nrows=5)
    output[sheet] = list(str(c) for c in df.columns)

with open("excel_schema.json", "w") as f:
    json.dump(output, f, indent=2)
