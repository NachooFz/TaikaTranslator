import json

with open("log/2026-05-29 - Technical test PRO-003_page_2_azure_response.json", "r", encoding="utf-8") as f:
    data = json.load(f)

paragraphs = data.get("analyzeResult", {}).get("paragraphs", [])
for idx, p in enumerate(paragraphs):
    content = p.get("content", "")
    regions = p.get("boundingRegions", [])
    if regions:
        poly = regions[0].get("polygon", [])
        # poly is [x1, y1, x2, y2, x3, y3, x4, y4]
        min_y = min(poly[1], poly[3], poly[5], poly[7])
        max_y = max(poly[1], poly[3], poly[5], poly[7])
        min_x = min(poly[0], poly[2], poly[4], poly[6])
        max_x = max(poly[0], poly[2], poly[4], poly[6])
        height = max_y - min_y
        print(f"Para {idx}: text='{content[:40]}' y_range=[{min_y:.1f}, {max_y:.1f}] height={height:.1f} x_range=[{min_x:.1f}, {max_x:.1f}]")
