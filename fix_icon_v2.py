#!/usr/bin/env python3
from PIL import Image
import os
import sys

source_image = sys.argv[1] if len(sys.argv) > 1 else "source_image.jpg"

script_dir = os.path.dirname(os.path.abspath(__file__))
base_dir = os.path.join(script_dir, "app", "src", "main", "res")

mipmap_sizes = {
    "mipmap-mdpi": (48, 48),
    "mipmap-hdpi": (72, 72),
    "mipmap-xhdpi": (96, 96),
    "mipmap-xxhdpi": (144, 144),
    "mipmap-xxxhdpi": (192, 192),
}

def ensure_dir(path):
    os.makedirs(path, exist_ok=True)

def create_foreground(img, size):
    target_width, target_height = size
    
    result = Image.new('RGBA', size, (0, 0, 0, 0))
    
    scale_w = target_width / img.width
    scale_h = target_height / img.height
    scale = min(scale_w, scale_h)
    
    new_width = int(img.width * scale)
    new_height = int(img.height * scale)
    
    resized = img.resize((new_width, new_height), Image.Resampling.LANCZOS)
    
    left = (target_width - new_width) // 2
    top = (target_height - new_height) // 2
    
    result.paste(resized, (left, top))
    
    return result

def main():
    print("Starting adaptive icon generation...")
    
    img = Image.open(source_image)
    
    if img.mode != 'RGBA':
        img = img.convert('RGBA')
    
    print(f"Source image size: {img.size}")
    
    print("\n=== Generating foreground layers (full image, no crop) ===")
    for folder, size in mipmap_sizes.items():
        output_dir = os.path.join(base_dir, folder)
        ensure_dir(output_dir)
        
        foreground = create_foreground(img, size)
        
        output_path = os.path.join(output_dir, "ic_launcher_foreground.png")
        foreground.save(output_path, "PNG")
        print(f"Created: {folder}/ic_launcher_foreground.png ({size[0]}x{size[1]}) - full image scaled")
        
        output_path2 = os.path.join(output_dir, "ic_launcher.png")
        foreground.save(output_path2, "PNG")
        
        output_path3 = os.path.join(output_dir, "ic_launcher_round.png")
        foreground.save(output_path3, "PNG")
    
    print("\n=== Creating adaptive icon XML ===")
    anydpi_dir = os.path.join(base_dir, "mipmap-anydpi-v26")
    ensure_dir(anydpi_dir)
    
    bg_xml = '''<?xml version="1.0" encoding="utf-8"?>
<vector android:height="108.0dp" android:width="108.0dp" android:viewportWidth="108.0" android:viewportHeight="108.0"
  xmlns:android="http://schemas.android.com/apk/res/android">
    <path android:fillColor="#3ddc84" android:pathData="M0,0h108v108h-108z" />
</vector>'''
    
    drawable_dir = os.path.join(base_dir, "drawable")
    ensure_dir(drawable_dir)
    
    with open(os.path.join(drawable_dir, "ic_launcher_background.xml"), "w") as f:
        f.write(bg_xml)
    print("Created: drawable/ic_launcher_background.xml")
    
    adaptive_xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>'''
    
    with open(os.path.join(anydpi_dir, "ic_launcher.xml"), "w") as f:
        f.write(adaptive_xml)
    print("Created: mipmap-anydpi-v26/ic_launcher.xml")
    
    with open(os.path.join(anydpi_dir, "ic_launcher_round.xml"), "w") as f:
        f.write(adaptive_xml)
    print("Created: mipmap-anydpi-v26/ic_launcher_round.xml")
    
    print("\n=== Generating drawable resources ===")
    
    avatar_size = (96, 96)
    avatar = create_foreground(img, avatar_size)
    avatar_path = os.path.join(drawable_dir, "avatar.png")
    avatar.save(avatar_path, "PNG")
    print(f"Created: drawable/avatar.png ({avatar_size[0]}x{avatar_size[1]})")
    
    if len(sys.argv) > 1 or os.path.exists(source_image):
        bg_width = 720
        ratio = img.height / img.width
        bg_height = int(bg_width * ratio)
        bg = img.resize((bg_width, bg_height), Image.Resampling.LANCZOS)
        bg_path = os.path.join(drawable_dir, "chat_background.png")
        bg.save(bg_path, "PNG")
        print(f"Created: drawable/chat_background.png ({bg_width}x{bg_height})")
    else:
        print("Skipped: chat_background.png (no source image provided)")
    
    print("\n=== All icons generated with FULL image (no crop)! ===")
    print("Icons will show full character, system will mask appropriately.")
    print(f"\nUsage: python fix_icon_v2.py <path_to_source_image.jpg>")

if __name__ == "__main__":
    main()
