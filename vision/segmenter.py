import os
import sys
import argparse
import json
import cv2
import numpy as np
import torch
from ultralytics import SAM

def parse_args():
    parser = argparse.ArgumentParser(description="TaikaTranslator Python Vision Helper")
    parser.add_argument("--input", required=True, help="Path to input page image (PNG/JPG)")
    parser.add_argument("--output-dir", required=True, help="Directory to save clean image and extracted artifacts")
    parser.add_argument("--confidence", type=float, default=0.5, help="Confidence threshold for YOLO/SAM")
    return parser.parse_args()

def union_or_deduplicate_boxes(boxes):
    if not boxes:
        return []
    # Sort boxes by area descending
    boxes = sorted(boxes, key=lambda b: (b[2] - b[0]) * (b[3] - b[1]), reverse=True)
    keep = []
    for box in boxes:
        x1, y1, x2, y2 = box
        # Check if box is already mostly contained within any box in keep
        overlap = False
        for k_box in keep:
            kx1, ky1, kx2, ky2 = k_box
            # Calculate intersection
            ix1 = max(x1, kx1)
            iy1 = max(y1, ky1)
            ix2 = min(x2, kx2)
            iy2 = min(y2, ky2)
            if ix2 > ix1 and iy2 > iy1:
                iarea = (ix2 - ix1) * (iy2 - iy1)
                area = (x2 - x1) * (y2 - y1)
                # If intersection is more than 50% of the smaller box's area, it's a duplicate
                if iarea / area > 0.5:
                    overlap = True
                    break
        if not overlap:
            keep.append(box)
    return keep

def extract_sam_segmentation(image_path, output_dir):
    """
    High-fidelity segmentation using Segment Anything Model (SAM) from Ultralytics
    primed with bounding box prompts derived from high-precision HSV color detection
    and B&W circular notary stamp spatial heuristics.
    This runs extremely fast on CPU (only a single decoder pass per box) and achieves
    perfect, smooth, organic boundaries for colored stamps, signatures, and seals.
    """
    img = cv2.imread(image_path)
    if img is None:
        raise ValueError(f"Could not read image: {image_path}")

    h, w, _ = img.shape
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    
    # 1. Run HSV contour detection to find bounding boxes of colored elements (prompt seeds)
    lower_gray = np.array([0, 70, 30])
    upper_gray = np.array([180, 255, 245])
    color_mask = cv2.inRange(hsv, lower_gray, upper_gray)

    thresh = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY_INV, 15, 8)
    combined = cv2.bitwise_and(thresh, thresh, mask=color_mask)
    
    # Dilation with a larger 35x35 kernel to group adjacent stamp parts (borders, text, crests) into a single block
    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (35, 35))
    dilated = cv2.dilate(combined, kernel, iterations=1)
    
    contours_color, _ = cv2.findContours(dilated, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    
    boxes = []
    
    # Mode 1: Chromatic Filter (Colored stamps/signatures)
    for contour in contours_color:
        x, y, cw, ch = cv2.boundingRect(contour)
        # Filter out noise or stray letters/words by raising bounds to 60x60
        if cw < 60 or ch < 60 or cw > w * 0.9 or ch > h * 0.9:
            continue
        # Filter out compression halos around standard black text using pixel density threshold
        roi_combined = combined[y:y+ch, x:x+cw]
        if cv2.countNonZero(roi_combined) < 150:
            continue
        # Refine bounding box to get a tight fit around actual non-zero colored pixels (strips 35px dilation padding)
        pts = cv2.findNonZero(roi_combined)
        if pts is not None:
            rx, ry, rcw, rch = cv2.boundingRect(pts)
            bx, by, bcw, bch = x + rx, y + ry, rcw, rch
            
            # Filter out printed text rows using aspect ratio and sub-contour density
            sub_roi = combined[by:by+bch, bx:bx+bcw]
            sub_cnts, _ = cv2.findContours(sub_roi, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
            aspect_ratio = bcw / float(max(1, bch))
            if aspect_ratio > 4.0 and len(sub_cnts) > 8:
                continue
                
            boxes.append([bx, by, bx + bcw, by + bch])

    # Mode 2: Spatial Heuristic for B&W circular notary stamps
    dilated_bw = cv2.dilate(thresh, kernel, iterations=1)
    contours_bw, _ = cv2.findContours(dilated_bw, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    for contour in contours_bw:
        x, y, cw, ch = cv2.boundingRect(contour)
        if 120 <= cw <= 500 and 120 <= ch <= 500:
            aspect_ratio = float(cw) / max(1, ch)
            if 0.75 <= aspect_ratio <= 1.35:
                if cw < w * 0.9 and ch < h * 0.9:
                    # Refine to tight coordinates of actual thresholded pixels inside the B&W stamp area
                    roi_thresh = thresh[y:y+ch, x:x+cw]
                    pts = cv2.findNonZero(roi_thresh)
                    if pts is not None:
                        rx, ry, rcw, rch = cv2.boundingRect(pts)
                        bx, by, bcw, bch = x + rx, y + ry, rcw, rch
                        boxes.append([bx, by, bx + bcw, by + bch])

    # Deduplicate overlapping boxes
    boxes = union_or_deduplicate_boxes(boxes)

    if not boxes:
        # If no elements found, return clean page immediately
        clean_filename = "clean_page.png"
        cv2.imwrite(os.path.join(output_dir, clean_filename), img)
        return clean_filename, []

    # 2. Load SAM model and predict using the detected boxes as prompts
    model = SAM("sam2_t.pt")
    
    # Predict using SAM with box prompts (takes less than 0.5s per box on CPU)
    results = model(image_path, bboxes=boxes, device="cpu", verbose=False)
    result = results[0]
    
    artifacts = []
    clean_img = img.copy()
    
    if result.masks is not None:
        masks_data = result.masks.data.cpu().numpy() # shape (num_masks, H, W)
        
        for i, box in enumerate(boxes):
            if i >= len(masks_data):
                break
            x1, y1, x2, y2 = box
            cw = x2 - x1
            ch = y2 - y1
            
            artifact_id = f"artifact_{i+1}"
            norm_box = {
                "top": float(y1) / h,
                "left": float(x1) / w,
                "width": float(cw) / w,
                "height": float(ch) / h
            }
            
            # Extract binary mask for this element
            mask = masks_data[i] # shape H x W
            
            # Crop the mask and image to the bounding box region
            mask_roi = mask[y1:y2, x1:x2]
            roi = img[y1:y2, x1:x2]
            
            # Generate alpha channel (transparency) based on the mask
            alpha = (mask_roi * 255).astype(np.uint8)
            # Smooth the edges slightly for premium anti-aliased transparency
            alpha = cv2.GaussianBlur(alpha, (3, 3), 0)
            
            b, g, r = cv2.split(roi)
            rgba = cv2.merge([b, g, r, alpha])
            
            # Save transparent PNG
            artifact_filename = f"{artifact_id}.png"
            artifact_path = os.path.join(output_dir, artifact_filename)
            cv2.imwrite(artifact_path, rgba)
            
            # White-out the exact pixels of the SAM mask on the clean page
            # Dilate the mask slightly to ensure no colored fringes/halo borders remain
            kernel_mask = cv2.getStructuringElement(cv2.MORPH_RECT, (3, 3))
            expanded_mask = cv2.dilate(mask.astype(np.uint8), kernel_mask, iterations=1)
            clean_img[expanded_mask == 1] = 255
            
            artifacts.append({
                "file": artifact_filename,
                "type": "signature" if cw > ch * 1.5 else "stamp",
                "boundingBox": norm_box
            })
            
    # Save the cleaned page image
    clean_filename = "clean_page.png"
    clean_path = os.path.join(output_dir, clean_filename)
    cv2.imwrite(clean_path, clean_img)
    
    return clean_filename, artifacts

def main():
    args = parse_args()
    os.makedirs(args.output_dir, exist_ok=True)
    
    print(f"[VisionHelper] Starting processing for {args.input}...")
    
    try:
        print("[VisionHelper] Running high-fidelity SAM-based segmenter with box prompts...")
        clean_file, artifacts = extract_sam_segmentation(args.input, args.output_dir)
        
        result = {
            "status": "SUCCESS",
            "cleanImage": clean_file,
            "artifacts": artifacts
        }
        
        # Print JSON to stdout so Java orchestrator can easily parse it
        print("RESULT_JSON:" + json.dumps(result))
        sys.exit(0)
        
    except Exception as e:
        error_result = {
            "status": "ERROR",
            "message": str(e)
        }
        print("RESULT_JSON:" + json.dumps(error_result), file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
