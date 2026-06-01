import json

with open("log/2026-05-29 - Technical test PRO-003_page_2_azure_response.json", "r", encoding="utf-8") as f:
    data = json.load(f)

print("Root keys:", list(data.keys()))
analyze_result = data.get("analyzeResult", {})
print("AnalyzeResult keys:", list(analyze_result.keys()))
paragraphs = analyze_result.get("paragraphs", [])
print("Number of paragraphs:", len(paragraphs))
if paragraphs:
    print("First paragraph:", paragraphs[0])
