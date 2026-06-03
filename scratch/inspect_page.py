import cv2
import numpy as np
import os

for page_num in [1, 2, 3]:
    img_path = f"output/page_{page_num}.png"
    if not os.path.exists(img_path):
        continue
    img = cv2.imread(img_path)
    h, w, _ = img.shape
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)

    lower_gray = np.array([0, 70, 30])
    upper_gray = np.array([180, 255, 245])
    color_mask = cv2.inRange(hsv, lower_gray, upper_gray)

    thresh = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY_INV, 15, 8)
    combined = cv2.bitwise_and(thresh, thresh, mask=color_mask)

    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (35, 35))
    dilated = cv2.dilate(combined, kernel, iterations=1)

    contours_color, _ = cv2.findContours(dilated, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    print(f"\n--- Page {page_num} (size={w}x{h}): Total contours found on color mask: {len(contours_color)} ---")
    for i, contour in enumerate(contours_color):
        x, y, cw, ch = cv2.boundingRect(contour)
        if cw < 60 or ch < 60 or cw > w * 0.9 or ch > h * 0.9:
            continue
        roi_combined = combined[y:y+ch, x:x+cw]
        nz = cv2.countNonZero(roi_combined)
        if nz < 150:
            continue
        
        pts = cv2.findNonZero(roi_combined)
        if pts is not None:
            rx, ry, rcw, rch = cv2.boundingRect(pts)
            bx, by, bcw, bch = x + rx, y + ry, rcw, rch
            
            # Check sub-contours inside the refined box (before dilation)
            sub_roi = combined[by:by+bch, bx:bx+bcw]
            sub_cnts, _ = cv2.findContours(sub_roi, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
            aspect_ratio = bcw / float(bch)
            print(f"Contour {i+1}: refined=({bx}, {by}), size={bcw}x{bch}, aspect_ratio={aspect_ratio:.2f}, sub_cnts={len(sub_cnts)}")
