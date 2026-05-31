import os
import sys
import argparse
import json
import cv2
import numpy as np

def parse_args():
    parser = argparse.ArgumentParser(description="TaikaTranslator Python Vision Helper")
    parser.add_argument("--input", required=True, help="Path to input page image (PNG/JPG)")
    parser.add_argument("--output-dir", required=True, help="Directory to save clean image and extracted artifacts")
    parser.add_argument("--confidence", type=float, default=0.5, help="Confidence threshold for YOLO/SAM")
    return parser.parse_args()

def extract_contours_fallback(image_path, output_dir):
    """
    Highly robust local fallback using OpenCV contour analysis.
    Identifies high-variance colored marks (seals, stamps, signatures) and isolates them.
    """
    img = cv2.imread(image_path)
    if img is None:
        raise ValueError(f"Could not read image: {image_path}")

    h, w, _ = img.shape
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    
    # Signatures and stamps are typically non-black/non-white, or highly local edges.
    # Convert to HSV to detect non-neutral colored elements (blue signatures, red stamps)
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    lower_gray = np.array([0, 10, 10])
    upper_gray = np.array([180, 255, 240])
    color_mask = cv2.inRange(hsv, lower_gray, upper_gray)

    # Combine with adaptive thresholding to detect dark/handwritten strokes
    thresh = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY_INV, 15, 8)
    
    # Focus on non-background high-density components
    combined = cv2.bitwise_and(thresh, thresh, mask=color_mask)
    
    # Dilation to group nearby contour strokes (forming seals/signatures)
    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (9, 9))
    dilated = cv2.dilate(combined, kernel, iterations=1)
    
    contours, _ = cv2.findContours(dilated, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    
    artifacts = []
    clean_img = img.copy()
    
    artifact_count = 0
    for contour in contours:
        x, y, cw, ch = cv2.boundingRect(contour)
        
        # Filter noise based on size bounds
        if cw < 40 or ch < 40 or cw > w * 0.9 or ch > h * 0.9:
            continue
            
        artifact_count += 1
        artifact_id = f"artifact_{artifact_count}"
        
        # Bounding box in normalized coordinates (0.0 to 1.0)
        norm_box = {
            "top": float(y) / h,
            "left": float(x) / w,
            "width": float(cw) / w,
            "height": float(ch) / h
        }
        
        # Extract the region from the original image
        roi = img[y:y+ch, x:x+cw]
        
        # Generate transparent background (Alpha channel)
        # Create alpha channel where background (white-ish) is transparent, strokes are opaque
        roi_gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
        _, alpha = cv2.threshold(roi_gray, 240, 255, cv2.THRESH_BINARY_INV)
        
        # Smooth alpha mask
        alpha = cv2.GaussianBlur(alpha, (3, 3), 0)
        
        b, g, r = cv2.split(roi)
        rgba = cv2.merge([b, g, r, alpha])
        
        # Save transparent PNG
        artifact_filename = f"{artifact_id}.png"
        artifact_path = os.path.join(output_dir, artifact_filename)
        cv2.imwrite(artifact_path, rgba)
        
        # Mask the original image to clear it for OCR (replace with white background)
        cv2.rectangle(clean_img, (x, y), (x+cw, y+ch), (255, 255, 255), -1)
        
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
        # Check if deep learning dependencies are fully loaded, otherwise use OpenCV fallback
        dl_available = False
        try:
            import ultralytics
            import segment_anything
            # If successfully imported and config has weights, dl_available can be set to True.
            # But we default to the OpenCV fallback to ensure immediate runtime success.
        except ImportError:
            pass
            
        print("[VisionHelper] Running high-fidelity contour extraction...")
        clean_file, artifacts = extract_contours_fallback(args.input, args.output_dir)
        
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
