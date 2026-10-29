from PIL import Image, ImageDraw, ImageFont


def generate_poster(directory, movie=False):
    image = Image.new('RGB', (400, 600))
    draw = ImageDraw.Draw(image)
    for y in range(600):
        blend = y / 600
        draw.line((0, y, 400, y), fill=(int(22 * (1 - blend) + 5 * blend),
                                      int(43 * (1 - blend) + 11 * blend),
                                      int(70 * (1 - blend) + 21 * blend)))
    draw.ellipse((155, 105, 365, 315), fill=(120, 174, 196) if not movie else (197, 156, 116))
    draw.polygon([(0, 310), (80, 245), (146, 296), (208, 265), (310, 326),
                  (400, 260), (400, 600), (0, 600)], fill=(16, 45, 65))
    draw.polygon([(0, 370), (100, 328), (190, 360), (320, 310),
                  (400, 354), (400, 600), (0, 600)], fill=(11, 32, 47))
    draw.polygon([(0, 410), (120, 389), (230, 405), (400, 390),
                  (400, 600), (0, 600)], fill=(7, 19, 31))
    regular = '/System/Library/Fonts/Supplemental/Arial.ttf'
    bold = '/System/Library/Fonts/Supplemental/Arial Bold.ttf'
    try:
        fonts = [ImageFont.truetype(regular, 13), ImageFont.truetype(bold, 57),
                 ImageFont.truetype(regular, 37), ImageFont.truetype(regular, 11)]
    except OSError:
        fonts = [ImageFont.load_default(size=size) for size in [13, 57, 37, 11]]
    draw.text((32, 33), 'ORIGINAL UI FIXTURE', font=fonts[0], fill=(178, 209, 224))
    draw.text((30, 450), 'QUIET' if movie else 'BEYOND', font=fonts[1], fill=(229, 243, 245))
    draw.text((32, 514), 'ORBIT' if movie else 'THE BLUE', font=fonts[2], fill=(196, 219, 228))
    draw.text((32, 577), 'SYNTHETIC TEST MEDIA', font=fonts[3], fill=(124, 154, 169))
    image.save(directory / ('poster-movie.jpg' if movie else 'poster.jpg'), quality=95)
